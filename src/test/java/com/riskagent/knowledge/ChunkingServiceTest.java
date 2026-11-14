package com.riskagent.knowledge;

import com.riskagent.config.AgentProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 切分器单元测试。切分是 RAG 效果的第一道闸门，这个模块必须被测试覆盖：
 * 改 chunkSize 时，这里能立刻告诉你切出来的片段有没有丢内容、有没有把标题带偏。
 */
class ChunkingServiceTest {

    private final ChunkingService service = new ChunkingService(
            new AgentProperties(120, 20, 4, 0.01, false, "classpath:kb/*.md"));

    private static final String MD = """
            # 测试制度

            ## 第一条 适用范围

            本制度适用于各营业网点。运营主管负第一责任，反洗钱岗负责复核。

            ## 第二条 报告标准

            当日单笔或累计人民币 5 万元以上的现金收付，应作为大额交易报告。外币等值 1 万美元以上同样适用。

            ## 第三条 时限

            大额交易报告应在交易发生后 5 个工作日内提交。
            """;

    @Test
    void 应该按标题切分并保留章节名() {
        List<Chunk> chunks = service.chunk("test.md", MD);

        assertTrue(chunks.size() >= 3, "三个章节至少切出三个片段，实际=" + chunks.size());
        assertTrue(chunks.stream().anyMatch(c -> c.section().contains("适用范围")));
        assertTrue(chunks.stream().anyMatch(c -> c.section().contains("报告标准")));
        assertTrue(chunks.stream().anyMatch(c -> c.section().contains("时限")));
        assertEquals("test.md", chunks.get(0).source());
    }

    @Test
    void 切片长度不应超过配置上限太多() {
        List<Chunk> chunks = service.chunk("test.md", MD);
        // 允许 overlap 带来的超出：上限 + 重叠 + 单行长度
        for (Chunk chunk : chunks) {
            assertTrue(chunk.text().length() <= 120 + 20 + 60,
                    "片段超长：" + chunk.text().length() + " → " + chunk.text());
        }
    }

    @Test
    void 关键数字不应被切断或丢失() {
        List<Chunk> chunks = service.chunk("test.md", MD);
        String all = chunks.stream().map(Chunk::text).reduce("", String::concat);
        assertTrue(all.contains("5 万元"), "金额必须完整保留");
        assertTrue(all.contains("5 个工作日"), "时限必须完整保留");
    }
}
