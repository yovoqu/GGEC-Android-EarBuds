package com.yovo.lib_ai.realtime;

import androidx.annotation.Nullable;

import java.math.BigDecimal;

/**
 * 一轮实时回答的用量。线上字段名的差异在协议解析里收口，这里只留已经对齐的数。
 */
public final class YoRealtimeUsage {

    private final int inputTokens;
    private final int outputTokens;
    private final int totalTokens;
    @Nullable
    private final Integer cachedInputTokens;
    @Nullable
    private final BigDecimal cost;

    public YoRealtimeUsage(
            int inputTokens,
            int outputTokens,
            int totalTokens,
            @Nullable Integer cachedInputTokens,
            @Nullable BigDecimal cost) {
        if (inputTokens < 0 || outputTokens < 0 || totalTokens < 0) {
            throw new IllegalArgumentException("token counts must not be negative");
        }
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.totalTokens = totalTokens;
        this.cachedInputTokens = cachedInputTokens;
        this.cost = cost;
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

    @Nullable
    public Integer cachedInputTokens() {
        return cachedInputTokens;
    }

    @Nullable
    public BigDecimal cost() {
        return cost;
    }
}
