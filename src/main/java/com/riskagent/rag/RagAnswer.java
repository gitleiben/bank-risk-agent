package com.riskagent.rag;

import com.riskagent.domain.Citation;

import java.util.List;

/**
 * RAG 问答结果。
 *
 * @param answer    答案正文，内含 [1] [2] 形式的引用标号
 * @param citations 引用来源明细，与标号一一对应
 * @param mode      生成模式：LLM（模型生成） / RETRIEVAL_ONLY（模型不可用时的降级，只给原文片段）
 * @param contexts  实际喂给模型的上下文片段数
 */
public record RagAnswer(String answer, List<Citation> citations, String mode, int contexts) {
}
