package com.yovo.lib_ai.chat;

import androidx.annotation.Nullable;

import java.math.BigDecimal;

/**
 * OpenAI 系（含兼容网关）的令牌用量。
 *
 * <p>缓存命中与费用是后加的：它们只对部分平台、部分模型出现，所以一律可空。
 * 读不到就是 null，库不做任何推算——按字符数估出来的账对不上真实账单，
 * 比没有更糟。{@link #cost()} 的币种由网关自己约定（OpenRouter 一类按美元），
 * 这里只搬运数字，不换汇、不格式化。</p>
 */
public final class YoChatUsage {

    private final int promptTokens;
    private final int completionTokens;
    private final int totalTokens;

    @Nullable
    private final Integer cachedTokens;
    @Nullable
    private final Integer cacheCreationInputTokens;
    @Nullable
    private final BigDecimal cost;

    /**
     * 只有三个令牌数的老构造器。缓存与费用保持未知，调用点无需改动。
     */
    public YoChatUsage(int promptTokens, int completionTokens, int totalTokens) {
        this(promptTokens, completionTokens, totalTokens, null, null, null);
    }

    public YoChatUsage(
            int promptTokens,
            int completionTokens,
            int totalTokens,
            @Nullable Integer cachedTokens,
            @Nullable Integer cacheCreationInputTokens,
            @Nullable BigDecimal cost) {
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
        this.cachedTokens = cachedTokens;
        this.cacheCreationInputTokens = cacheCreationInputTokens;
        this.cost = cost;
    }

    public int promptTokens() {
        return promptTokens;
    }

    public int completionTokens() {
        return completionTokens;
    }

    public int totalTokens() {
        return totalTokens;
    }

    /**
     * 命中提示缓存的令牌数，来自 {@code prompt_tokens_details.cached_tokens}、
     * {@code cache_read_input_tokens} 或 {@code prompt_cache_hit_tokens}。
     */
    @Nullable
    public Integer cachedTokens() {
        return cachedTokens;
    }

    /**
     * 写入提示缓存的令牌数（Anthropic 的 {@code cache_creation_input_tokens}）。
     */
    @Nullable
    public Integer cacheCreationInputTokens() {
        return cacheCreationInputTokens;
    }

    /**
     * 网关直接给出的这一次请求费用。不是按令牌单价算的估价。
     */
    @Nullable
    public BigDecimal cost() {
        return cost;
    }
}
