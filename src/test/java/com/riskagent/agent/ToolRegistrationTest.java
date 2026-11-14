package com.riskagent.agent;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 上下文装配测试：确认 Spring 容器能起来，并且 4 个业务工具真的被注册成 ToolCallback。
 *
 * <p>这条测试的价值在于：MCP Server 暴露哪些工具，取决于 {@link ToolCallbackProvider} 返回什么。
 * 一旦有人改了工具方法签名或漏加 @Tool，这里会立刻红。</p>
 *
 * <p>不需要 Ollama：模型不可用只影响生成，不影响容器装配与工具注册。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ToolRegistrationTest {

    @Autowired
    private ToolCallbackProvider toolCallbackProvider;

    @Test
    void 四个风控工具都应该注册成功() {
        String[] names = Arrays.stream(toolCallbackProvider.getToolCallbacks())
                .map(cb -> cb.getToolDefinition().name())
                .toArray(String[]::new);

        assertEquals(4, names.length, "工具数量不对：" + Arrays.toString(names));
        Set<String> expected = Set.of("ruleLookup", "caseRecall", "accountProfile", "dispatchTicket");
        Set<String> actual = Arrays.stream(names).collect(Collectors.toSet());
        assertTrue(actual.containsAll(expected), "缺少工具：" + expected + " 实际：" + actual);
    }
}
