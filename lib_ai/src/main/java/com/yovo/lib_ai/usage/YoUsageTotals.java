package com.yovo.lib_ai.usage;

import androidx.annotation.NonNull;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * 一组用量记录的合计：请求次数、各类 token 累计、按币种分列的金额。
 *
 * <p>金额不做跨币种相加。{@link #costs()} 的键是币种，值是各币种的合计；
 * 要总数就用 {@link #cost(String)} 取某个币种，而不是把所有钱加成一个数。
 *
 * @param requests                 记录条数，即请求次数
 * @param inputTokens              输入 token 累计
 * @param outputTokens             输出 token 累计
 * @param totalTokens              总 token 累计
 * @param cachedInputTokens        命中缓存的输入 token 累计
 * @param cacheCreationInputTokens 写入缓存的输入 token 累计
 * @param costs                    币种到金额的合计，按币种字典序，不可变
 */
public record YoUsageTotals(
        long requests,
        long inputTokens,
        long outputTokens,
        long totalTokens,
        long cachedInputTokens,
        long cacheCreationInputTokens,
        @NonNull Map<String, BigDecimal> costs) {

    public static final YoUsageTotals EMPTY =
            new YoUsageTotals(0L, 0L, 0L, 0L, 0L, 0L, Map.of());

    public YoUsageTotals {
        if (requests < 0 || inputTokens < 0 || outputTokens < 0
                || totalTokens < 0 || cachedInputTokens < 0 || cacheCreationInputTokens < 0) {
            throw new IllegalArgumentException("usage totals must not be negative");
        }
        if (costs == null) {
            throw new IllegalArgumentException("costs is required");
        }
        costs = costs.isEmpty() ? Map.of() : Collections.unmodifiableMap(new TreeMap<>(costs));
    }

    /** 某个币种的合计；没有该币种的记录时为 0。 */
    @NonNull
    public BigDecimal cost(@NonNull String currency) {
        BigDecimal value = costs.get(currency);
        return value == null ? BigDecimal.ZERO : value;
    }

    /** 有没有任何金额。为 {@code false} 说明这些请求平台都没回报费用。 */
    public boolean hasCosts() {
        return !costs.isEmpty();
    }

    public boolean isEmpty() {
        return requests == 0;
    }
}
