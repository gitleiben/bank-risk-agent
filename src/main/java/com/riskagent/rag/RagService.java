package com.riskagent.rag;

import com.riskagent.config.AgentProperties;
import com.riskagent.config.AiConfig;
import com.riskagent.config.ModelHealth;
import com.riskagent.domain.Citation;
import com.riskagent.knowledge.Chunk;
import com.riskagent.knowledge.KeywordIndex;
import com.riskagent.knowledge.KnowledgeBaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 制度问答（RAG 主链路）。
 *
 * <p>链路：问题 → 混合检索 TopK → 拼上下文（带 [1][2] 标号）→ 系统提示词约束 → 大模型生成 → 回填引用。</p>
 *
 * <p>三个工程细节：</p>
 * <ol>
 *   <li><b>检索不到就不硬答</b>：召回为空直接返回"知识库未覆盖"，避免金融场景最忌讳的编造。</li>
 *   <li><b>引用由代码回填，不由模型自由发挥</b>：模型只负责在正文里写 [n]，来源、章节、得分由系统填，
 *       这样"引用"才可信。</li>
 *   <li><b>模型不可用时降级</b>：直接返回命中片段原文，标注 RETRIEVAL_ONLY，保证演示与压测不中断。</li>
 * </ol>
 */
@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    private final ChatClient chatClient;
    private final KnowledgeBaseService knowledgeBase;
    private final AgentProperties props;
    private final ModelHealth modelHealth;
    private final String systemPrompt;

    public RagService(ChatClient chatClient, KnowledgeBaseService knowledgeBase,
                      AgentProperties props, ModelHealth modelHealth) {
        this.chatClient = chatClient;
        this.knowledgeBase = knowledgeBase;
        this.props = props;
        this.modelHealth = modelHealth;
        this.systemPrompt = AiConfig.readPrompt(AiConfig.QA_SYSTEM_PROMPT);
    }

    public RagAnswer answer(String question) {
        List<KeywordIndex.Scored> hits = knowledgeBase.search(question, props.topK());
        if (hits.isEmpty()) {
            return new RagAnswer("知识库中没有检索到与该问题相关的制度条款。建议补充语料或换个问法。",
                    List.of(), "NO_CONTEXT", 0);
        }

        List<Citation> citations = new ArrayList<>();
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < hits.size(); i++) {
            Chunk chunk = hits.get(i).chunk();
            int no = i + 1;
            context.append(chunk.label(no)).append('\n')
                    .append(chunk.text()).append("\n\n");
            citations.add(new Citation(no, chunk.source(), chunk.section(),
                    snippet(chunk.text()), round(hits.get(i).score())));
        }

        // 先探针，避免 Spring AI 的重试把一次请求拖到两分钟
        if (!modelHealth.available()) {
            return new RagAnswer("【降级模式：本地模型未就绪，先给出命中的制度原文】\n\n" + context,
                    citations, "RETRIEVAL_ONLY", hits.size());
        }

        String userMessage = "参考资料：\n\n" + context + "\n用户问题：" + question;
        try {
            String answer = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userMessage)
                    .call()
                    .content();
            return new RagAnswer(answer, citations, "LLM", hits.size());
        } catch (Exception e) {
            log.warn("大模型调用失败，降级为仅检索模式：{}", e.getMessage());
            String fallback = "【降级模式：本地模型未就绪，先给出命中的制度原文】\n\n"
                    + context;
            return new RagAnswer(fallback, citations, "RETRIEVAL_ONLY", hits.size());
        }
    }

    private static String snippet(String text) {
        String oneLine = text.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= 90 ? oneLine : oneLine.substring(0, 90) + "…";
    }

    private static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }
}
