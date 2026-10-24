package com.ggec.earbuds.domain.ai.model;

import androidx.annotation.Nullable;

import java.math.BigDecimal;

/**
 * 一次请求的令牌用量与费用。
 *
 * <p>为什么合计要单独存：部分平台把推理、缓存命中的令牌算进它自己给的合计，
 * 自己拿输入加输出会和账单对不上，所以平台的合计优先，只有平台没给（0）时才退回求和。
 * 费用只记录网关明确返回的数字，币种由网关约定（OpenRouter 一类按美元），
 * 这里不换汇、不按单价估算——估出来的账不能拿去核对。</p>
 *
 * <p>缓存相关的两个数都是可空的：普通请求、不支持缓存的模型上根本没有这一项。
 * 读缓存和写缓存的单价不同，所以它们是两个字段，不是同一个数。</p>
 */
public record AiUsage(
        int inputTokens,
        int outputTokens,
        int totalTokens,
        @Nullable BigDecimal cost,
        @Nullable Integer cachedTokens,
        @Nullable Integer cacheCreationTokens) {

    public AiUsage {
        inputTokens = Math.max(0, inputTokens);
        outputTokens = Math.max(0, outputTokens);
        totalTokens = totalTokens > 0 ? totalTokens : inputTokens + outputTokens;
        cachedTokens = cachedTokens == null || cachedTokens < 0 ? null : cachedTokens;
        cacheCreationTokens = cacheCreationTokens == null || cacheCreationTokens < 0
                ? null
                : cacheCreationTokens;
    }

    /**
     * 只知道令牌数的一笔（普通请求、平台不报费用和缓存）。
     * 用命名工厂而不是第二个构造器：调用处一眼能看出"其余字段是未知，不是 0"。
     */
    public static AiUsage ofTokens(int inputTokens, int outputTokens) {
        return new AiUsage(inputTokens, outputTokens, 0, null, null, null);
    }

    public boolean hasCost() {
        return cost != null;
    }

    /**
     * 没有费用就当作零，便于累加。要不要区分“没报费用”请用 {@link #hasCost()}。
     */
    public BigDecimal costOrZero() {
        return cost == null ? BigDecimal.ZERO : cost;
    }
}
