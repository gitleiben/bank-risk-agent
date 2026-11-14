package com.riskagent.agent;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Agent 的工具箱（Tool Calling 的"手脚"）。
 *
 * <p>四个工具对应风控处置岗位真实会做的四步查证：查制度条款 → 查同类历史案例 → 查账户画像 →
 * 提交分派。方法上的 {@link Tool} 描述会作为工具说明交给大模型，模型据此决定调哪个、传什么参数，
 * <b>所以描述写得越像业务语言，工具调用准确率越高</b>——这是 Agent 效果调优的第一现场，不是靠改模型。</p>
 *
 * <p>数据说明：以下为脱敏的示例数据，用于演示工具调用链路；真实接入时把方法体换成
 * 现有 Java 风控平台的 REST / Dubbo 调用即可（里程碑 3 会把这套工具封装成 MCP Server，
 * 让外部 IDE、其他 Agent 也能复用同一批工具）。</p>
 */
@Component
public class RiskToolbox {

    @Tool(description = "查询某类预警适用的风控规则条款：输入预警类型或关键词，返回规则编号、触发条件与处置要求")
    public String ruleLookup(@ToolParam(description = "预警类型或关键词，例如：大额可疑交易") String keyword) {
        if (keyword == null) {
            return "未提供查询关键词";
        }
        String k = keyword.replace(" ", "");
        if (k.contains("大额") || k.contains("可疑")) {
            return """
                    规则编号：R-101 大额与可疑交易报告
                    触发条件：单笔或当日累计现金收付 5 万元以上；短期内资金分散转入、集中转出。
                    处置要求：T+1 内完成可疑交易初筛，5 个工作日内决定是否提交大额/可疑交易报告；
                              涉恐名单命中必须当日上报。
                    依据：《大额交易和可疑交易报告管理办法》示例条款第八条、第十二条。
                    """;
        }
        if (k.contains("开销户") || k.contains("开户")) {
            return """
                    规则编号：R-205 账户异常开销户
                    触发条件：同一客户 30 日内开立 3 个以上账户；开户后 7 日内销户且无交易。
                    处置要求：核实开户意愿与真实用途，必要时限制非柜面渠道；涉嫌买卖账户的移交反洗钱岗。
                    依据：《人民币银行结算账户管理办法》示例条款第二十一条。
                    """;
        }
        if (k.contains("时段") || k.contains("夜间") || k.contains("非柜面")) {
            return """
                    规则编号：R-308 非柜面异常时段交易
                    触发条件：22:00—06:00 期间单账户非柜面交易超过 5 笔或累计 20 万元以上。
                    处置要求：电话回访核实交易意愿；无法联系或回答矛盾的，暂停非柜面渠道并留存录音。
                    依据：《电子银行安全评估指引》示例条款与行内运营监督细则第 4.3 条。
                    """;
        }
        return "未匹配到规则编号，建议人工确认预警类型（可用：大额可疑交易 / 账户异常开销户 / 非柜面异常时段交易）";
    }

    @Tool(description = "检索同类历史预警案例，返回案例编号、当时判定结论与最终处置结果，用于类比参考")
    public String caseRecall(@ToolParam(description = "预警类型，例如：大额可疑交易") String alertType) {
        if (alertType == null || alertType.isBlank()) {
            return "未提供预警类型";
        }
        if (alertType.contains("大额") || alertType.contains("可疑")) {
            return """
                    案例 C-2024-037：某商贸公司账户 3 日内分散转入 46 笔、集中转出 2 笔，金额 320 万元。
                      判定：高度可疑，客户无法说明资金来源与交易对手关系。
                      结果：提交可疑交易报告，账户只收不付，移交反洗钱岗，后续配合公安调查。
                    案例 C-2024-112：个体经营户当日现金存入 68 万元。
                      判定：低风险，资金来源为集市摊位营业款，已留存说明。
                      结果：补录大额交易报告，无需进一步处置。
                    """;
        }
        if (alertType.contains("开销户") || alertType.contains("开户")) {
            return """
                    案例 C-2025-009：同一身份证 20 日内开立 4 个账户，开户后无交易即销户。
                      判定：涉嫌出租出借账户。
                      结果：拒绝开户 2 户，存量 2 户限制非柜面，纳入重点关注名单。
                    """;
        }
        return "暂无同类历史案例，建议按制度条款走首次处置流程";
    }

    @Tool(description = "查询账户画像：客户基本信息、近30日交易特征、历史预警与处置记录")
    public String accountProfile(@ToolParam(description = "银行账号") String accountNo) {
        if (accountNo == null || accountNo.isBlank()) {
            return "未提供账号";
        }
        return switch (accountNo) {
            case "6222020200112233" -> """
                    账号：6222020200112233  户名：某某商贸有限公司  开户行：示例支行
                    客户类型：对公，开户 4 个月，无信贷关系
                    近30日：交易 87 笔，累计转入 320 万 / 转出 318 万，对手方 41 个（多为个人账户）
                    特征：夜间交易占比 62%，多笔金额接近 4.9 万元
                    历史：近 90 天 2 次同类预警，均已归档
                    """;
            case "6217000099887766" -> """
                    账号：6217000099887766  户名：王某某  开户行：示例支行
                    客户类型：个人，代发工资客户，开户 6 年
                    近30日：交易 12 笔，累计 8.6 万元，主要为消费与转账
                    特征：交易时段正常，对手方稳定
                    历史：无预警记录
                    """;
            default -> "账号 " + accountNo + " 未在示例数据中，返回空画像（真实环境改为调用客户信息系统）";
        };
    }

    @Tool(description = "生成处置分派单号并记录分派动作，返回分派单号。仅在高/中风险且已确定处置动作后调用")
    public String dispatchTicket(@ToolParam(description = "工单号") String ticketNo,
                                 @ToolParam(description = "分派岗位，例如：反洗钱岗、支行运营主管") String assignTo) {
        String no = "DSP-" + java.time.LocalDate.now().toString().replace("-", "")
                + "-" + Math.abs((ticketNo + assignTo).hashCode() % 10000);
        return "分派成功，分派单号 " + no + "，接收岗位：" + assignTo + "，工单 " + ticketNo + " 已置为该岗位待办";
    }
}
