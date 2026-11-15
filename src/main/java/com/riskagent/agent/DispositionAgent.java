package com.riskagent.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskagent.config.AgentProperties;
import com.riskagent.config.AiConfig;
import com.riskagent.config.ModelHealth;
import com.riskagent.domain.AlertTicket;
import com.riskagent.domain.Citation;
import com.riskagent.domain.Disposition;
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
 * 预警工单处置 Agent。
 *
 * <p>这是整个项目里"Agent"和"RAG"的分界线：RAG 只会读资料回答，Agent 要<b>自己决定查什么、查几次，
 * 最后落成一个可执行的处置结论</b>。</p>
 *
 * <p>执行流程：</p>
 * <ol>
 *   <li><b>取证据</b>：用工单的预警类型 + 描述去知识库混合检索，拿到相关制度条款（RAG）。</li>
 *   <li><b>调工具</b>：把 {@link RiskToolbox} 交给模型，由模型自主决定调规则查询 / 案例回溯 / 账户画像，
 *       拿到原始业务数据（Tool Calling，模型可以连续调多次，不需要写死顺序）。</li>
 *   <li><b>出结论</b>：强制结构化输出——风险等级、判定依据、处置动作、分派岗位，缺一不可。</li>
 *   <li><b>可审计</b>：引用来源由代码回填，保证每一条结论都能追到条款原文。</li>
 * </ol>
 */
@Service
public class DispositionAgent {

    private static final Logger log = LoggerFactory.getLogger(DispositionAgent.class);

    private final ChatClient chatClient;
    private final RiskToolbox toolbox;
    private final KnowledgeBaseService knowledgeBase;
    private final AgentProperties props;
    private final ModelHealth modelHealth;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String systemPrompt;

    public DispositionAgent(ChatClient chatClient, RiskToolbox toolbox,
                            KnowledgeBaseService knowledgeBase, AgentProperties props,
                            ModelHealth modelHealth) {
        this.chatClient = chatClient;
        this.toolbox = toolbox;
        this.knowledgeBase = knowledgeBase;
        this.props = props;
        this.modelHealth = modelHealth;
        this.systemPrompt = AiConfig.readPrompt(AiConfig.DISPOSE_SYSTEM_PROMPT);
    }

    public Disposition dispose(AlertTicket ticket) {
        List<KeywordIndex.Scored> hits = knowledgeBase.search(
                ticket.alertType() + " " + safe(ticket.description()), props.topK());
        List<Citation> citations = toCitations(hits);

        if (!modelHealth.available()) {
            return fallback(ticket, citations, "本地模型未就绪（Ollama 未启动或模型未 pull）");
        }

        String userMessage = buildUserMessage(ticket, hits);
        try {
            String raw = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userMessage)
                    .tools(toolbox)
                    .call()
                    .content();
            return parse(raw, citations);
        } catch (Exception e) {
            log.warn("处置 Agent 执行失败：{}", e.getMessage());
            return fallback(ticket, citations, e.getMessage());
        }
    }

    private String buildUserMessage(AlertTicket ticket, List<KeywordIndex.Scored> hits) {
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < hits.size(); i++) {
            Chunk chunk = hits.get(i).chunk();
            context.append(chunk.label(i + 1)).append('\n').append(chunk.text()).append("\n\n");
        }
        return """
                【预警工单】
                工单号：%s
                预警类型：%s
                账号：%s   户名：%s   金额：%s   渠道：%s   发生时间：%s
                预警描述：%s

                【制度参考（已检索，可直接引用，编号用于最终答案）】
                %s

                请按流程处置：先判断需要补充哪些事实，用工具查证，再给出结论。
                """.formatted(
                safe(ticket.ticketNo()), safe(ticket.alertType()), safe(ticket.accountNo()),
                safe(ticket.customerName()), safe(ticket.amount()), safe(ticket.channel()),
                safe(ticket.occurredAt()), safe(ticket.description()),
                context);
    }

    /**
     * 解析模型返回的 JSON。真实项目里模型输出不稳定，这里做两层兜底：
     * 先剥 ```json 代码块，再截取第一个 { 到最后一个 }；仍失败就整段当作结论文本。
     */
    private Disposition parse(String raw, List<Citation> citations) {
        String json = extractJson(raw);
        try {
            JsonNode node = objectMapper.readTree(json);
            List<String> actions = new ArrayList<>();
            if (node.has("actions")) {
                node.get("actions").forEach(a -> actions.add(a.asText()));
            }
            return new Disposition(
                    node.path("riskLevel").asText("未判定"),
                    node.path("conclusion").asText(""),
                    node.path("basis").asText(""),
                    actions,
                    node.path("assignTo").asText("支行运营主管"),
                    citations);
        } catch (Exception e) {
            log.warn("结构化解析失败，按纯文本返回：{}", e.getMessage());
            return new Disposition("未判定", raw, "", List.of("人工复核模型输出"), "支行运营主管", citations);
        }
    }

    private static String extractJson(String raw) {
        if (raw == null) {
            return "{}";
        }
        String s = raw.replaceAll("(?s)```(?:json)?", "").trim();
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        return (start >= 0 && end > start) ? s.substring(start, end + 1) : s;
    }

    /** 模型不可用时的降级：给出制度依据 + 明确的"待人工处置"，绝不假装已判定。 */
    private Disposition fallback(AlertTicket ticket, List<Citation> citations, String reason) {
        String basis = citations.isEmpty()
                ? "未检索到匹配制度条款"
                : citations.get(0).source() + " · " + citations.get(0).section();
        return new Disposition(
                "待人工判定",
                "本地模型不可用（" + safe(reason) + "），已返回检索到的制度依据，请人工按流程处置工单 "
                        + safe(ticket.ticketNo()) + "。",
                basis,
                List.of("人工核对制度条款", "补充账户画像与交易明细", "确定处置结论后再分派"),
                "支行运营主管",
                citations);
    }

    private static List<Citation> toCitations(List<KeywordIndex.Scored> hits) {
        List<Citation> citations = new ArrayList<>();
        for (int i = 0; i < hits.size(); i++) {
            Chunk chunk = hits.get(i).chunk();
            String snippet = chunk.text().replaceAll("\\s+", " ").trim();
            citations.add(new Citation(i + 1, chunk.source(), chunk.section(),
                    snippet.length() > 90 ? snippet.substring(0, 90) + "…" : snippet,
                    Math.round(hits.get(i).score() * 1000) / 1000.0));
        }
        return citations;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
