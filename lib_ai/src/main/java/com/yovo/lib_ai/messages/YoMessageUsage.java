package com.yovo.lib_ai.messages;

import androidx.annotation.Nullable;

import java.math.BigDecimal;

/**
 * Anthropic Messages 的用量。
 *
 * <p>这个协议不返回合计令牌数，只有输入和输出，所以这里不硬凑一个 total，
 * 交给上层自己决定怎么相加。提示缓存的两项是 Anthropic 独有的计费维度：
 * 读缓存便宜、写缓存更贵，混成一个数字会算错钱，因此分开保留、都可空。</p>
 */
public final class YoMessageUsage {

    private final int inputTokens;
    private final int outputTokens;

    @Nullable
    private final Integer cachedInputTokens;
    @Nullable
    private final Integer cacheCreationInputTokens;
    @Nullable
    private final BigDecimal cost;

    /**
     * 只有输入输出的老构造器，缓存与费用保持未知。
     */
    public YoMessageUsage(int inputTokens, int outputTokens) {
        this(inputTokens, outputTokens, null, null, null);
    }

    public YoMessageUsage(
            int inputTokens,
            int outputTokens,
            @Nullable Integer cachedInputTokens,
            @Nullable Integer cacheCreationInputTokens,
            @Nullable BigDecimal cost) {
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
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

    /**
     * 命中已有缓存的输入令牌，即 {@code cache_read_input_tokens}。
     */
    @Nullable
    public Integer cachedInputTokens() {
        return cachedInputTokens;
    }

    /**
     * 本次写入缓存的输入令牌，即 {@code cache_creation_input_tokens}。
     */
    @Nullable
    public Integer cacheCreationInputTokens() {
        return cacheCreationInputTokens;
    }

    /**
     * 网关额外给出的费用，只在兼容网关上有值。
     */
    @Nullable
    public BigDecimal cost() {
        return cost;
    }
}
