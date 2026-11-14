package com.riskagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 银行运营监督与风险预警 Agent —— 应用入口。
 *
 * <p>项目定位：个人 AI 预研项目，用真实的银行风控业务场景（预警工单处置、制度问答），
 * 验证「本地大模型 + RAG + Tool Calling」在金融合规（数据不出域）场景下的可行性。
 * 不是银行生产系统，也不冒充生产系统。</p>
 *
 * <p>启动：{@code ./scripts/dev.sh}（会自动设置 JDK / Maven 环境变量后运行）。</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class RiskAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(RiskAgentApplication.class, args);
    }
}
