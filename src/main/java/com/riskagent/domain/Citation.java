package com.riskagent.domain;

/**
 * 引用来源。RAG 在金融场景能不能用，关键就看这一条：答案必须能溯回原文。
 *
 * @param index   答案里的标号，对应 [1] [2]
 * @param source  来源文件名，如 01-大额交易与可疑交易报告制度.md
 * @param section 章节标题
 * @param snippet 命中片段（截断展示）
 * @param score   相似度/相关度得分
 */
public record Citation(int index, String source, String section, String snippet, double score) {
}
