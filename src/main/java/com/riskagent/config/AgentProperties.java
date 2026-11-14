package com.riskagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 业务参数（全部可在 application.yml 的 risk-agent 节点下调整，改完不用改代码）。
 *
 * @param chunkSize     每个知识切片的字符上限。太小→检索碎片化、语义不完整；太大→命中噪音多
 * @param chunkOverlap  相邻切片重叠字符数，避免一句话被切断后两边都查不到
 * @param topK          检索召回条数，直接决定喂给大模型的上下文长度（也直接影响流量与延迟）
 * @param minScore      关键词检索的最低分阈值，低于它的片段当作不相关丢弃
 * @param ingestOnStart 是否启动时就把知识库向量化（本地模型没装好时建议 false）
 * @param kbLocation    知识库文件位置，支持 classpath: 前缀与通配符
 */
@ConfigurationProperties(prefix = "risk-agent")
public record AgentProperties(
        int chunkSize,
        int chunkOverlap,
        int topK,
        double minScore,
        boolean ingestOnStart,
        String kbLocation) {

    /** 兜底默认值：即便 yml 少写了某个字段，也不会出现 0 或 null 导致运行期报错。 */
    public AgentProperties {
        if (chunkSize <= 0) {
            chunkSize = 420;
        }
        if (chunkOverlap < 0) {
            chunkOverlap = 80;
        }
        if (topK <= 0) {
            topK = 4;
        }
        if (minScore <= 0) {
            minScore = 0.01;
        }
        if (kbLocation == null || kbLocation.isBlank()) {
            kbLocation = "classpath:kb/*.md";
        }
    }
}
