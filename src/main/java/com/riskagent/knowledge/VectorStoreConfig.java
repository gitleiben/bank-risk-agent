package com.riskagent.knowledge;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 向量库装配。
 *
 * <p>里程碑 1 用 Spring AI 自带的 {@link SimpleVectorStore}（纯内存、余弦相似度、零外部依赖），
 * 目的是先把「切分 → 向量化 → 检索 → 拼上下文 → 生成」这条链路跑通、看明白。
 * 里程碑 2 再换成 Elasticsearch（岗位硬要求），只需换一个 VectorStore 实现，上层代码不动——
 * 这就是面向接口编程在这里的实际价值，面试可以直接讲。</p>
 */
@Configuration
public class VectorStoreConfig {

    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}
