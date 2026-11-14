package com.riskagent.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 本地模型可用性探针。
 *
 * <p>为什么需要它：Spring AI 默认带重试（10 次、间隔递增），Ollama 没启动时一次问答要卡两分钟
 * 才走到降级分支，演示和压测都会很难看。这里用 700ms 的轻量探测先判断"模型在不在"，
 * 不在就直接走降级，把不可用变成秒级可感知的状态，而不是超时火焰。</p>
 *
 * <p>结果缓存 10 秒，避免每次请求都多打一次探针。</p>
 */
@Component
public class ModelHealth {

    private static final Logger log = LoggerFactory.getLogger(ModelHealth.class);

    private final String baseUrl;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(700))
            .build();

    private volatile boolean available = false;
    private volatile long checkedAt = 0L;

    public ModelHealth(@Value("${spring.ai.ollama.base-url:http://localhost:11434}") String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @PostConstruct
    public void probeOnStartup() {
        boolean ok = check();
        log.info("本地模型探针 [{}] → {}", baseUrl, ok ? "可用" : "不可用（问答将走降级模式）");
    }

    /** 模型服务是否可用（带 10 秒缓存）。 */
    public boolean available() {
        if (System.currentTimeMillis() - checkedAt > 10_000L) {
            check();
        }
        return available;
    }

    private synchronized boolean check() {
        checkedAt = System.currentTimeMillis();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/tags"))
                    .timeout(Duration.ofMillis(700))
                    .GET()
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            available = response.statusCode() == 200;
        } catch (Exception e) {
            available = false;
        }
        return available;
    }
}
