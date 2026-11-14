package com.riskagent.api;

import com.riskagent.agent.DispositionAgent;
import com.riskagent.domain.AlertTicket;
import com.riskagent.domain.Disposition;
import com.riskagent.config.ModelHealth;
import com.riskagent.eval.EvalReport;
import com.riskagent.eval.EvalService;
import com.riskagent.knowledge.KnowledgeBaseService;
import com.riskagent.rag.RagAnswer;
import com.riskagent.rag.RagService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 对外接口。刻意保持"少而清晰"，每个接口对应一个可演示的能力：
 *
 * <ul>
 *   <li>{@code POST /api/qa} —— 制度问答（RAG）</li>
 *   <li>{@code POST /api/alert/dispose} —— 预警工单处置（Agent + 工具调用）</li>
 *   <li>{@code POST /api/kb/reindex} —— 重建知识库与向量索引</li>
 *   <li>{@code GET  /api/eval/run} —— 跑评测集，输出检索/答案准确率</li>
 *   <li>{@code GET  /api/stats} —— 运行状态，用于演示页与排查</li>
 * </ul>
 */
@RestController
@RequestMapping("/api")
public class AgentController {

    /** 问答请求体。 */
    public record AskRequest(String question) {
    }

    private final RagService ragService;
    private final DispositionAgent dispositionAgent;
    private final KnowledgeBaseService knowledgeBase;
    private final EvalService evalService;
    private final ModelHealth modelHealth;
    private final String modelName;

    public AgentController(RagService ragService, DispositionAgent dispositionAgent,
                           KnowledgeBaseService knowledgeBase, EvalService evalService,
                           ModelHealth modelHealth,
                           @Value("${spring.ai.ollama.chat.options.model:unknown}") String modelName) {
        this.ragService = ragService;
        this.dispositionAgent = dispositionAgent;
        this.knowledgeBase = knowledgeBase;
        this.evalService = evalService;
        this.modelHealth = modelHealth;
        this.modelName = modelName;
    }

    @PostMapping("/qa")
    public RagAnswer qa(@RequestBody AskRequest request) {
        return ragService.answer(request.question());
    }

    @PostMapping("/alert/dispose")
    public Disposition dispose(@RequestBody AlertTicket ticket) {
        return dispositionAgent.dispose(ticket);
    }

    @PostMapping("/kb/reindex")
    public Map<String, Object> reindex() {
        int chunks = knowledgeBase.reload();
        int vectors = knowledgeBase.ingestVectors();
        return Map.of("chunks", chunks, "vectors", vectors, "vectorReady", knowledgeBase.vectorReady());
    }

    @GetMapping("/eval/run")
    public EvalReport eval(@RequestParam(defaultValue = "false") boolean withLlm) {
        return evalService.run(withLlm);
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return Map.of(
                "model", modelName,
                "modelAvailable", modelHealth.available(),
                "chunks", knowledgeBase.chunkCount(),
                "vectorReady", knowledgeBase.vectorReady());
    }
}
