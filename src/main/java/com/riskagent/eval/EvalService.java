package com.riskagent.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskagent.config.AgentProperties;
import com.riskagent.knowledge.KeywordIndex;
import com.riskagent.knowledge.KnowledgeBaseService;
import com.riskagent.rag.RagAnswer;
import com.riskagent.rag.RagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 评测服务。跑法：{@code curl "localhost:8080/api/eval/run?withLlm=false"}（只测检索，秒级）
 * 或 {@code ...?withLlm=true}（连模型一起测，慢但完整）。
 */
@Service
public class EvalService {

    private static final Logger log = LoggerFactory.getLogger(EvalService.class);

    /** 一条评测用例：问题 + 期望关键词 + 期望命中的来源文件。 */
    public record EvalCase(String question, List<String> expectKeywords, String expectSource) {
    }

    private final KnowledgeBaseService knowledgeBase;
    private final RagService ragService;
    private final AgentProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private List<EvalCase> cases;

    public EvalService(KnowledgeBaseService knowledgeBase, RagService ragService, AgentProperties props) {
        this.knowledgeBase = knowledgeBase;
        this.ragService = ragService;
        this.props = props;
    }

    public EvalReport run(boolean withLlm) {
        List<EvalReport.CaseResult> results = new ArrayList<>();
        int retrievalHits = 0;
        int answerHits = 0;

        for (EvalCase c : loadCases()) {
            List<KeywordIndex.Scored> hits = knowledgeBase.search(c.question(), props.topK());
            String joined = hits.stream().map(h -> h.chunk().text()).reduce("", (a, b) -> a + b);
            Set<String> sources = new LinkedHashSet<>();
            hits.forEach(h -> sources.add(h.chunk().source()));

            boolean sourceHit = c.expectSource() == null || sources.contains(c.expectSource());
            boolean keywordHit = coverage(c.expectKeywords(), joined) >= 0.5;
            boolean retrievedHit = sourceHit && keywordHit;
            if (retrievedHit) {
                retrievalHits++;
            }

            Boolean answerHit = null;
            if (withLlm) {
                RagAnswer answer = ragService.answer(c.question());
                answerHit = coverage(c.expectKeywords(), answer.answer()) >= 0.5;
                if (answerHit) {
                    answerHits++;
                }
            }
            results.add(new EvalReport.CaseResult(c.question(), retrievedHit, answerHit,
                    sources.stream().limit(3).toList()));
        }

        int total = results.size();
        double retrieval = total == 0 ? 0 : (double) retrievalHits / total;
        double answer = (!withLlm || total == 0) ? 0 : (double) answerHits / total;
        log.info("评测完成：用例={}，检索命中率={}，答案命中率={}", total,
                Math.round(retrieval * 100) / 100.0, Math.round(answer * 100) / 100.0);
        return new EvalReport(total, Math.round(retrieval * 1000) / 1000.0,
                Math.round(answer * 1000) / 1000.0, results);
    }

    /** 期望关键词在文本中的覆盖率，0~1。比对前统一去掉空格与全角空格，"5万元" 与 "5 万元" 视为同一写法。 */
    private static double coverage(List<String> keywords, String text) {
        if (keywords == null || keywords.isEmpty()) {
            return 1.0;
        }
        String normalized = normalize(text);
        long hit = keywords.stream().filter(k -> normalized.contains(normalize(k))).count();
        return (double) hit / keywords.size();
    }

    private static String normalize(String s) {
        return s == null ? "" : s.replaceAll("[\\s\\u00A0\\u3000]", "");
    }

    private List<EvalCase> loadCases() {
        if (cases != null) {
            return cases;
        }
        try (InputStream in = new ClassPathResource("eval/qa-gold.json").getInputStream()) {
            cases = objectMapper.readValue(in, new TypeReference<List<EvalCase>>() {
            });
        } catch (IOException e) {
            log.warn("评测集读取失败：{}", e.getMessage());
            cases = List.of();
        }
        return cases;
    }

    /** 供演示页展示：返回评测集问题清单，不含期望值。 */
    public List<Map<String, String>> questions() {
        return loadCases().stream().map(c -> Map.of("question", c.question())).toList();
    }
}
