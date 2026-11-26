package com.grindset.backend.llm;

/**
 * 一次模型调用的 token 用量。
 *
 * <p>上游返回 usage 时是精确值;部分厂商流式不返回 usage,
 * 由输入输出字符数粗略估算,此时 estimated=true,只能用于兜底记账。</p>
 */
public record LlmUsage(int inputTokens, int outputTokens, int cachedTokens, boolean estimated) {

    /** 拿不到任何用量时的兜底,成本记 0 */
    public static final LlmUsage EMPTY = new LlmUsage(0, 0, 0, true);

    public int totalTokens() {
        return inputTokens + outputTokens;
    }
}
