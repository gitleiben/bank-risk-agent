package com.riskagent.eval;

import java.util.List;

/**
 * 评测报告。
 *
 * <p>做 Agent 项目最容易漏、但面试官最爱问的一环：<b>你怎么知道它准不准？</b>
 * 没有评测的项目只能叫 Demo。这里给出最小可用评测：人工标注问题 → 期望关键词与来源文件 →
 * 分别统计"检索命中率"和"答案命中率"，改切分策略/权重前后各跑一遍，就有可对比的数字。</p>
 *
 * @param total             用例数
 * @param retrievalAccuracy 检索命中率：TopK 里是否包含期望来源且覆盖关键词
 * @param answerAccuracy    答案命中率：模型答案是否覆盖期望关键词（withLlm=true 时才有意义）
 * @param cases             逐条结果
 */
public record EvalReport(int total, double retrievalAccuracy, double answerAccuracy, List<CaseResult> cases) {

    /**
     * 单条用例结果。
     *
     * @param question     问题
     * @param retrievedHit 检索是否命中
     * @param answerHit    答案是否命中（未调用模型时为 null）
     * @param topSources   实际召回的来源文件（去重前 3 个），便于排查召回错在哪
     */
    public record CaseResult(String question, boolean retrievedHit, Boolean answerHit, List<String> topSources) {
    }
}
