package com.riskagent.config;

import com.riskagent.agent.RiskToolbox;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP Server 装配：把 {@link RiskToolbox} 里的 @Tool 方法暴露成标准 MCP 工具。
 *
 * <p>意义在于「同一套工具，三种用法」：</p>
 * <ul>
 *   <li>本项目的 Agent 通过 Tool Calling 直接调用；</li>
 *   <li>Claude Desktop / Cursor / 其他 Agent 通过 MCP 协议调用同一批工具，不用重写；</li>
 *   <li>后续接真实风控接口时，只需要改 Toolbox 里一个方法体，所有调用方同时生效。</li>
 * </ul>
 *
 * <p>这里的关键点：MCP 解决的不是"能不能调工具"，而是"工具定义能不能跨客户端复用"。</p>
 */
@Configuration
public class McpServerConfig {

    @Bean
    ToolCallbackProvider riskToolCallbackProvider(RiskToolbox riskToolbox) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(riskToolbox)
                .build();
    }
}
