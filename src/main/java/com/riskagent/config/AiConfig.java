package com.riskagent.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * AI 基础设施装配。
 *
 * <p>这里只做两件事：把 Ollama 自动配置出来的 {@link ChatModel} 包成 Spring AI 的
 * {@link ChatClient}（更顺手的对话门面），以及把提示词模板从 classpath 读出来缓存成字符串。
 * 提示词不硬编码在 Java 里——改口径、调风格都在 resources/prompts 下改文件即可，
 * 这是做 Agent 项目最容易被低估的工程习惯。</p>
 */
@Configuration
public class AiConfig {

    /** RAG 问答的系统提示词：约束"只依据资料回答、必须带引用编号、资料不足就承认不知道"。 */
    public static final String QA_SYSTEM_PROMPT = "prompts/qa-system.md";

    /** 预警工单处置 Agent 的系统提示词：定义角色、工具使用纪律、输出结构。 */
    public static final String DISPOSE_SYSTEM_PROMPT = "prompts/dispose-system.md";

    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    /**
     * 读取 classpath 下的提示词文件。用 Spring 的 ClassPathResource 而不是自己拼路径，
     * 打成 jar 之后依然可用（这是很多人本地跑得通、打包就 500 的经典坑）。
     */
    public static String readPrompt(String classpathLocation) {
        try (var in = new ClassPathResource(classpathLocation).getInputStream()) {
            return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("读取提示词失败: " + classpathLocation, e);
        }
    }
}
