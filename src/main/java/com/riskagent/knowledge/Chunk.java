package com.riskagent.knowledge;

/**
 * 一个知识切片。
 *
 * <p>切片是 RAG 的最小检索单元，也是"证据"的最小单位——答案里引用的 [1][2] 最终都指向某个切片。
 * 所以切片必须带来源与章节，不能只有一段裸文本。</p>
 *
 * @param id      稳定 id：source + 序号，便于去重与溯源
 * @param source  来源文件
 * @param section 章节标题（从 Markdown 的 # 标题提取）
 * @param text    切片正文
 * @param index   在文档内的序号
 */
public record Chunk(String id, String source, String section, String text, int index) {

    /** 给大模型看的引用标签，例如「[1] 大额交易制度 · 第三章」。 */
    public String label(int no) {
        return "[" + no + "] " + source.replace(".md", "") + " · " + section;
    }
}
