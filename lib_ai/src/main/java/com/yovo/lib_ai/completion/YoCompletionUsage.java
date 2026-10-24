package com.yovo.lib_ai.completion;

import androidx.annotation.Nullable;

import java.math.BigDecimal;

/**
 * 令牌用量。三条协议字段名不同，这里统一成输入、输出、合计。
 *
 * <p>缓存与费用是可选的：只有部分平台在部分模型上返回，读不到就是 null。
 * 合计沿用老语义——平台给了就用平台的，没给（0）才算输入加输出，
 * 因为有些平台把推理和缓存计入合计，自己相加会和账单对不上。</p>
 */
public final class YoCompletionUsage {

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
     * 只有三项令牌数的老构造器，调用点无需改动。
     */
    public YoCompletionUsage(int inputTokens, int outputTokens, int totalTokens) {
        this(inputTokens, outputTokens, totalTokens, null, null, null);
    }

    public YoCompletionUsage(
            int inputTokens,
            int outputTokens,
            int totalTokens,
            @Nullable Integer cachedInputTokens,
            @Nullable Integer cacheCreationInputTokens,
            @Nullable BigDecimal cost) {
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.totalTokens = totalTokens > 0 ? totalTokens : inputTokens + outputTokens;
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

    /**
     * 命中提示缓存的输入令牌，读不到为 null。
     */
    @Nullable
    public Integer cachedInputTokens() {
        return cachedInputTokens;
    }

    /**
     * 写入提示缓存的输入令牌（目前只有 Anthropic 报），读不到为 null。
     */
    @Nullable
    public Integer cacheCreationInputTokens() {
        return cacheCreationInputTokens;
    }

    /**
     * 网关直接给出的这一次请求费用，读不到为 null。
     */
    @Nullable
    public BigDecimal cost() {
        return cost;
    }
}
