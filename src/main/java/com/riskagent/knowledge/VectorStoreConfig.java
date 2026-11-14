package com.riskagent.knowledge;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 向量库装配。
 *
 * <p>默认走内存版 {@link SimpleVectorStore}：零外部依赖，方便本地演示和写单测。
 * 加 {@code --spring.profiles.active=es} 时切换到 Elasticsearch（见 application-es.yml），
 * 检索相关的上层代码一行都不用改——这正是 Spring AI 把 VectorStore 抽象出来的价值，
 * 也是面试时"你怎么做技术选型/怎么控制切换成本"的现成答案。</p>
 */
@Configuration
public class VectorStoreConfig {

    @Bean
    @ConditionalOnProperty(name = "spring.ai.vectorstore.elasticsearch.enabled",
            havingValue = "false", matchIfMissing = true)
    VectorStore simpleVectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}
