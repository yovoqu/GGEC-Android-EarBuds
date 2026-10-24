package com.yovo.lib_ai.responses;

import androidx.annotation.Nullable;

import java.math.BigDecimal;

/**
 * Responses 协议的用量。协议本身就带合计，所以这里保留平台给的合计；
 * 缓存命中的令牌藏在 {@code input_tokens_details.cached_tokens} 里，单独取出来。
 */
public final class YoResponseUsage {

    private final int inputTokens;
    private final int outputTokens;
    private final int totalTokens;

    @Nullable
    private final Integer cachedInputTokens;
    @Nullable
    private final Integer cacheCreationInputTokens;
    @Nullable
    private final BigDecimal cost;

    /**
     * 只有三项令牌数的老构造器，缓存与费用保持未知。
     */
    public YoResponseUsage(int inputTokens, int outputTokens, int totalTokens) {
        this(inputTokens, outputTokens, totalTokens, null, null, null);
    }

    public YoResponseUsage(
            int inputTokens,
            int outputTokens,
            int totalTokens,
            @Nullable Integer cachedInputTokens,
            @Nullable Integer cacheCreationInputTokens,
            @Nullable BigDecimal cost) {
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.totalTokens = totalTokens;
        this.cachedInputTokens = cachedInputTokens;
        this.cacheCreationInputTokens = cacheCreationInputTokens;
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
    public Integer cacheCreationInputTokens() {
        return cacheCreationInputTokens;
    }

    @Nullable
    public BigDecimal cost() {
        return cost;
    }
}
