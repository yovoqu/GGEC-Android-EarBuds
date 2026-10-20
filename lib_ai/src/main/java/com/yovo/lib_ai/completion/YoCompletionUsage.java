package com.yovo.lib_ai.completion;

/**
 * 令牌用量。三条协议字段名不同，这里统一成输入、输出、合计。
 */
public final class YoCompletionUsage {

    private final int inputTokens;
    private final int outputTokens;
    private final int totalTokens;

    public YoCompletionUsage(int inputTokens, int outputTokens, int totalTokens) {
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.totalTokens = totalTokens > 0 ? totalTokens : inputTokens + outputTokens;
    }

    public int inputTokens() {
        return inputTokens;
    }

    public int outputTokens() {
        return outputTokens;
    }

    public int totalTokens() {
        return totalTokens;
    }
}
