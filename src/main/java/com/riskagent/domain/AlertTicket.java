package com.riskagent.domain;

/**
 * 预警工单（示例结构，字段对齐银行运营监督系统的常见预警报文）。
 *
 * @param ticketNo    工单号
 * @param alertType   预警类型，如「大额可疑交易」「账户频繁开销户」「非柜面异常时段交易」
 * @param accountNo   账号
 * @param customerName 户名
 * @param amount      涉及金额（元）
 * @param occurredAt  发生时间
 * @param channel     交易渠道，如 柜面 / 网银 / 手机银行 / ATM
 * @param description 预警描述
 */
public record AlertTicket(
        String ticketNo,
        String alertType,
        String accountNo,
        String customerName,
        String amount,
        String occurredAt,
        String channel,
        String description) {
}
