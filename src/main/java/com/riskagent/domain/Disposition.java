package com.riskagent.domain;

/**
 * 处置建议（Agent 的结构化输出）。
 *
 * <p>为什么一定要结构化：银行场景里"给一句话建议"没法落地，必须能落表、能分派、能审计。
 * 所以 Agent 最后一步固定输出：风险等级 + 判定依据 + 处置动作 + 分派岗位 + 引用来源。</p>
 *
 * @param riskLevel  风险等级：高 / 中 / 低
 * @param conclusion 结论（1-2 句）
 * @param basis      判定依据，来自制度与历史案例
 * @param actions    处置动作清单
 * @param assignTo   分派岗位，如「支行运营主管」「反洗钱岗」
 * @param citations  引用的知识来源编号与标题
 */
public record Disposition(
        String riskLevel,
        String conclusion,
        String basis,
        java.util.List<String> actions,
        String assignTo,
        java.util.List<Citation> citations) {
}
