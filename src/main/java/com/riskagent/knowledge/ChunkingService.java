package com.riskagent.knowledge;

import com.riskagent.config.AgentProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档切分器：把一篇 Markdown 制度文件切成若干带章节信息的片段。
 *
 * <p>切分策略（面试常被追问的点）：</p>
 * <ol>
 *   <li><b>先按标题分段</b>：制度文档的语义边界天然在条款标题处，按固定字数硬切会把"第八条"
 *       的适用条件和结论切到两个片段里，检索出来就是半句话。</li>
 *   <li><b>再按段落累积</b>到 chunkSize，超了就切，且保留上一片尾部 overlap 个字符，
 *       避免答案刚好压在切口上。</li>
 *   <li><b>空白行视为段落边界</b>，不做跨段拼接。</li>
 * </ol>
 *
 * <p>为什么不直接上 LangChain 的递归切分器：在制度类长文上，标题感知的切分对"条款问答"的
 * 召回提升非常明显，而且实现只要几十行，完全可控、可解释。</p>
 */
@Component
public class ChunkingService {

    private final AgentProperties props;

    public ChunkingService(AgentProperties props) {
        this.props = props;
    }

    public List<Chunk> chunk(String source, String markdown) {
        List<Chunk> chunks = new ArrayList<>();
        String section = "正文";
        StringBuilder buffer = new StringBuilder();
        int index = 0;

        for (String rawLine : markdown.split("\n")) {
            String line = rawLine.strip();

            // 1. 标题行：更新章节名，并把上一章节残留的缓冲先落盘
            if (line.startsWith("#")) {
                index = flush(source, section, buffer, chunks, index, false);
                section = line.replaceAll("^#+\\s*", "").trim();
                if (section.isEmpty()) {
                    section = "正文";
                }
                continue;
            }

            // 2. 空行：段落边界，跳过（段落信息已经通过换行保留在 buffer 里）
            if (line.isEmpty()) {
                continue;
            }

            // 3. 超长：先落盘当前缓冲，再把上一片的尾巴作为 overlap 带过来
            if (buffer.length() > 0 && buffer.length() + line.length() > props.chunkSize()) {
                index = flush(source, section, buffer, chunks, index, true);
            }
            buffer.append(line).append('\n');
        }
        flush(source, section, buffer, chunks, index, false);
        return chunks;
    }

    /**
     * 把缓冲写成一个切片。
     *
     * @param keepOverlap 是否保留尾部重叠（只有"因为太长而切"才需要，章节结束时不需要）
     * @return 下一个可用序号
     */
    private int flush(String source, String section, StringBuilder buffer, List<Chunk> out,
                      int index, boolean keepOverlap) {
        String text = buffer.toString().strip();
        if (text.isEmpty()) {
            return index;
        }
        out.add(new Chunk(source + "#" + index, source, section, text, index));
        index++;

        String overlap = "";
        if (keepOverlap && props.chunkOverlap() > 0 && text.length() > props.chunkOverlap()) {
            overlap = text.substring(text.length() - props.chunkOverlap());
        }
        buffer.setLength(0);
        if (!overlap.isEmpty()) {
            buffer.append(overlap).append('\n');
        }
        return index;
    }
}
