package com.yovo.lib_ai.usage;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * 累加器：一边加记录一边把总数和三个切面同时算出来。
 *
 * <p>不放在公开 API 里，是因为它可变；对外只暴露不可变的 {@link YoUsageSummary}。
 * 账本用它保存"开户以来"的累计，{@link YoUsageSummary#of} 用它做离线重算，两边口径一致。
 */
final class YoUsageAccumulator {

    private long requests;
    private long inputTokens;
    private long outputTokens;
    private long totalTokens;
    private long cachedInputTokens;
    private long cacheCreationInputTokens;
    private final Map<String, BigDecimal> costs = new TreeMap<>();
    private final Map<String, YoUsageAccumulator> byPlatform = new TreeMap<>();
    private final Map<String, YoUsageAccumulator> byModel = new TreeMap<>();
    private final Map<YoUsageSource, YoUsageAccumulator> bySource = new EnumMap<>(YoUsageSource.class);

    void add(YoUsageRecord record) {
        addToSelf(record);
        byPlatform.computeIfAbsent(record.platformId(), key -> new YoUsageAccumulator())
                .addToSelf(record);
        byModel.computeIfAbsent(record.model(), key -> new YoUsageAccumulator())
                .addToSelf(record);
        bySource.computeIfAbsent(record.source(), key -> new YoUsageAccumulator())
                .addToSelf(record);
    }

    private void addToSelf(YoUsageRecord record) {
        requests++;
        inputTokens += record.inputTokens();
        outputTokens += record.outputTokens();
        totalTokens += record.totalTokens();
        Integer cached = record.cachedInputTokens();
        if (cached != null) {
            cachedInputTokens += cached;
        }
        Integer cacheCreation = record.cacheCreationInputTokens();
        if (cacheCreation != null) {
            cacheCreationInputTokens += cacheCreation;
        }
        BigDecimal cost = record.cost();
        if (cost != null) {
            costs.merge(record.currency(), cost, BigDecimal::add);
        }
    }

    YoUsageTotals totals() {
        return new YoUsageTotals(requests, inputTokens, outputTokens, totalTokens,
                cachedInputTokens, cacheCreationInputTokens, costs);
    }

    /** 清零，用于账本的 {@code clear()}。 */
    void clear() {
        requests = 0L;
        inputTokens = 0L;
        outputTokens = 0L;
        totalTokens = 0L;
        cachedInputTokens = 0L;
        cacheCreationInputTokens = 0L;
        costs.clear();
        byPlatform.clear();
        byModel.clear();
        bySource.clear();
    }

    YoUsageSummary summary() {
        Map<String, YoUsageTotals> platforms = new TreeMap<>();
        byPlatform.forEach((key, value) -> platforms.put(key, value.totals()));
        Map<String, YoUsageTotals> models = new TreeMap<>();
        byModel.forEach((key, value) -> models.put(key, value.totals()));
        Map<YoUsageSource, YoUsageTotals> sources = new EnumMap<>(YoUsageSource.class);
        bySource.forEach((key, value) -> sources.put(key, value.totals()));
        return new YoUsageSummary(totals(), platforms, models, sources);
    }
}
