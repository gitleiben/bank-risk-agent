package com.riskagent.knowledge;

import com.riskagent.config.AgentProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 知识库服务：负责「装载 → 切分 → 建索引 → 混合检索」。
 *
 * <p>检索采用<b>混合（Hybrid）</b>策略：关键词分与向量分加权合并。
 * 金融制度问答里，纯向量检索经常被语义相近但条款不同的内容带偏，掺入关键词分能显著提升准确率。
 * 向量检索不可用时（本地模型没启动）自动退化为纯关键词检索，系统不整体挂掉。</p>
 */
@Service
public class KnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);

    private final AgentProperties props;
    private final ChunkingService chunking;
    private final KeywordIndex keywordIndex;
    private final VectorStore vectorStore;

    private final List<Chunk> chunks = new ArrayList<>();
    private final AtomicBoolean vectorReady = new AtomicBoolean(false);

    public KnowledgeBaseService(AgentProperties props, ChunkingService chunking,
                                KeywordIndex keywordIndex, VectorStore vectorStore) {
        this.props = props;
        this.chunking = chunking;
        this.keywordIndex = keywordIndex;
        this.vectorStore = vectorStore;
    }

    /** 启动即装载文本（不依赖模型，秒级完成），向量化按配置决定是否预热。 */
    @PostConstruct
    public void init() {
        int count = reload();
        log.info("知识库装载完成，切片数={}，来源={}", count, props.kbLocation());
        if (props.ingestOnStart()) {
            ingestVectors();
        }
    }

    /** 重新读取文件并切分、重建关键词索引。返回切片总数。 */
    public synchronized int reload() {
        chunks.clear();
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources(props.kbLocation());
            List<Resource> sorted = new ArrayList<>(List.of(resources));
            sorted.sort(Comparator.comparing(r -> String.valueOf(r.getFilename())));
            for (Resource resource : sorted) {
                String filename = resource.getFilename();
                if (filename == null) {
                    continue;
                }
                String markdown = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
                List<Chunk> part = chunking.chunk(filename, markdown);
                chunks.addAll(part);
                log.info("  装载 {} → {} 个切片", filename, part.size());
            }
        } catch (IOException e) {
            log.warn("读取知识库失败: {}", e.getMessage());
        }
        keywordIndex.rebuild(chunks);
        return chunks.size();
    }

    /**
     * 把切片写入向量库。
     *
     * @return 写入条数；失败返回 -1（通常是 Ollama 没启动或 embedding 模型没 pull）
     */
    public synchronized int ingestVectors() {
        if (chunks.isEmpty()) {
            reload();
        }
        try {
            List<Document> documents = chunks.stream()
                    .map(c -> new Document(c.text(), Map.of(
                            "chunkId", c.id(),
                            "source", c.source(),
                            "section", c.section())))
                    .toList();
            vectorStore.add(documents);
            vectorReady.set(true);
            log.info("向量化完成，共 {} 条", documents.size());
            return documents.size();
        } catch (Exception e) {
            vectorReady.set(false);
            log.warn("向量化失败（不影响关键词检索）：{}", e.getMessage());
            return -1;
        }
    }

    /**
     * 混合检索。
     *
     * <p>权重 0.45 关键词 / 0.55 向量：制度类问答里精确条款词权重不能太低，
     * 但整体还是向量更适合处理"换个说法问同一件事"。这组权重是靠评测集调出来的，
     * 不是拍脑袋——里程碑 4 的评测脚本会给出调参前后对比。</p>
     */
    public List<KeywordIndex.Scored> search(String question, int topK) {
        int k = topK > 0 ? topK : props.topK();
        Map<String, Double> merged = new HashMap<>();
        Map<String, Chunk> byId = new HashMap<>();

        for (KeywordIndex.Scored s : keywordIndex.search(question, k * 3, props.minScore())) {
            merged.merge(s.chunk().id(), s.score() * 0.45, Double::sum);
            byId.put(s.chunk().id(), s.chunk());
        }

        if (vectorReady.get()) {
            try {
                List<Document> docs = vectorStore.similaritySearch(
                        SearchRequest.builder().query(question).topK(k * 3).build());
                for (Document doc : docs) {
                    String chunkId = String.valueOf(doc.getMetadata().get("chunkId"));
                    Chunk chunk = byId.get(chunkId);
                    if (chunk == null) {
                        chunk = findById(chunkId);
                        if (chunk == null) {
                            continue;
                        }
                        byId.put(chunkId, chunk);
                    }
                    double score = doc.getScore() == null ? 0.5 : doc.getScore();
                    merged.merge(chunkId, score * 0.55, Double::sum);
                }
            } catch (Exception e) {
                log.warn("向量检索失败，本轮回退关键词检索：{}", e.getMessage());
            }
        }

        return merged.entrySet().stream()
                .map(e -> new KeywordIndex.Scored(byId.get(e.getKey()), e.getValue()))
                .sorted((a, b) -> Double.compare(b.score(), a.score()))
                .limit(k)
                .toList();
    }

    private Chunk findById(String chunkId) {
        return chunks.stream().filter(c -> c.id().equals(chunkId)).findFirst().orElse(null);
    }

    public boolean vectorReady() {
        return vectorReady.get();
    }

    public int chunkCount() {
        return chunks.size();
    }
}
