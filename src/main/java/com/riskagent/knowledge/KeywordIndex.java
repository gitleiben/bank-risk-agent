package com.riskagent.knowledge;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 关键词检索（BM25 思路的简化实现，零依赖、离线可用）。
 *
 * <p>为什么向量检索之外还要留这一路：</p>
 * <ul>
 *   <li>制度问答里有大量<b>精确词</b>——"第八条""可疑交易报告""五级分类"，这类词向量检索容易
 *       被语义相近的表述稀释，关键词检索反而更准；</li>
 *   <li>本地模型没装好、Embedding 服务挂了的时候，系统还能降级工作，不至于整个 Demo 哑火。</li>
 * </ul>
 *
 * <p>中文分词没有引入 jieba/HanLP，用<b>二元组（bigram）</b>切分：「可疑交易」→「可疑」「疑交」「交易」。
 * 银行术语多是双字词组合，bigram 召回已经够用，且完全无词典依赖。</p>
 */
@Component
public class KeywordIndex {

    /** 检索结果：切片 + 归一化到 0~1 的相关度。 */
    public record Scored(Chunk chunk, double score) {
    }

    private final List<Chunk> chunks = new ArrayList<>();
    /** 词 → 命中的切片下标，倒排索引。 */
    private final Map<String, List<Integer>> postings = new HashMap<>();

    public synchronized void rebuild(List<Chunk> newChunks) {
        chunks.clear();
        postings.clear();
        chunks.addAll(newChunks);
        for (int i = 0; i < chunks.size(); i++) {
            for (String token : tokenize(chunks.get(i).text())) {
                postings.computeIfAbsent(token, k -> new ArrayList<>()).add(i);
            }
        }
    }

    public synchronized int size() {
        return chunks.size();
    }

    /**
     * 检索：把 query 分词后，用 idf 加权累计每个切片的命中分，取 topK。
     */
    public synchronized List<Scored> search(String query, int topK, double minScore) {
        if (chunks.isEmpty()) {
            return List.of();
        }
        Set<String> tokens = tokenize(query);
        Map<Integer, Double> raw = new HashMap<>();
        int total = chunks.size();

        for (String token : tokens) {
            List<Integer> hit = postings.get(token);
            if (hit == null) {
                continue;
            }
            // idf：越少见的词，命中越值钱（"报表"vs"可疑交易报告"）
            double idf = Math.log((total + 1.0) / (hit.size() + 1.0)) + 1.0;
            for (int docIdx : hit) {
                raw.merge(docIdx, idf, Double::sum);
            }
        }
        if (raw.isEmpty()) {
            return List.of();
        }

        double max = raw.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
        return raw.entrySet().stream()
                .map(e -> new Scored(chunks.get(e.getKey()), e.getValue() / max))
                .filter(s -> s.score() >= minScore)
                .sorted((a, b) -> Double.compare(b.score(), a.score()))
                .limit(topK)
                .toList();
    }

    /** 中文按二元组切，英文数字按词切，统一小写去重。 */
    static Set<String> tokenize(String text) {
        Set<String> tokens = new LinkedHashSet<>();
        if (text == null || text.isBlank()) {
            return tokens;
        }
        String lower = text.toLowerCase();
        StringBuilder cjk = new StringBuilder();

        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (isCjk(c)) {
                cjk.append(c);
            } else {
                flushCjk(cjk, tokens);
                if (Character.isLetterOrDigit(c)) {
                    int j = i;
                    while (j < lower.length() && Character.isLetterOrDigit(lower.charAt(j))
                            && !isCjk(lower.charAt(j))) {
                        j++;
                    }
                    String word = lower.substring(i, j);
                    if (word.length() >= 2) {
                        tokens.add(word);
                    }
                    i = j - 1;
                }
            }
        }
        flushCjk(cjk, tokens);
        return tokens;
    }

    private static void flushCjk(StringBuilder buf, Collection<String> out) {
        String s = buf.toString();
        buf.setLength(0);
        if (s.length() == 1) {
            return;
        }
        for (int i = 0; i + 1 < s.length(); i++) {
            out.add(s.substring(i, i + 2));
        }
    }

    private static boolean isCjk(char c) {
        return c >= 0x4E00 && c <= 0x9FFF;
    }
}
