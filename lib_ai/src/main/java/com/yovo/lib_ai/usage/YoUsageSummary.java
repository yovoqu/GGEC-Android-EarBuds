package com.yovo.lib_ai.usage;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 用量汇总：一个总数，外加按平台、按模型、按链路三个切面。
 *
 * <p>三个切面各自都能单独对上总数——缺平台或模型的记录会归到
 * {@link YoUsageRecord#UNSPECIFIED}，不会被悄悄丢掉。
 *
 * @param overall    全部记录合计
 * @param byPlatform 平台 id 到合计，按 id 字典序
 * @param byModel    模型 id 到合计，按 id 字典序
 * @param bySource   链路到合计，按枚举声明顺序
 */
public record YoUsageSummary(
        @NonNull YoUsageTotals overall,
        @NonNull Map<String, YoUsageTotals> byPlatform,
        @NonNull Map<String, YoUsageTotals> byModel,
        @NonNull Map<YoUsageSource, YoUsageTotals> bySource) {

    public static final YoUsageSummary EMPTY =
            new YoUsageSummary(YoUsageTotals.EMPTY, Map.of(), Map.of(), Map.of());

    public YoUsageSummary {
        if (overall == null) {
            throw new IllegalArgumentException("overall is required");
        }
        byPlatform = immutableSorted(byPlatform);
        byModel = immutableSorted(byModel);
        bySource = immutableSources(bySource);
    }

    /** 按给定记录现算一份汇总，用于核对或离线重算。 */
    @NonNull
    public static YoUsageSummary of(@NonNull List<YoUsageRecord> records) {
        YoUsageAccumulator accumulator = new YoUsageAccumulator();
        for (YoUsageRecord record : records) {
            accumulator.add(record);
        }
        return accumulator.summary();
    }

    public boolean isEmpty() {
        return overall.isEmpty();
    }

    /** 某个平台的合计；没有记录时返回 {@link YoUsageTotals#EMPTY}。 */
    @NonNull
    public YoUsageTotals byPlatform(@NonNull String platformId) {
        YoUsageTotals totals = byPlatform.get(platformId);
        return totals == null ? YoUsageTotals.EMPTY : totals;
    }

    /** 某个模型的合计；没有记录时返回 {@link YoUsageTotals#EMPTY}。 */
    @NonNull
    public YoUsageTotals byModel(@NonNull String model) {
        YoUsageTotals totals = byModel.get(model);
        return totals == null ? YoUsageTotals.EMPTY : totals;
    }

    /** 某条链路的合计；没有记录时返回 {@link YoUsageTotals#EMPTY}。 */
    @NonNull
    public YoUsageTotals bySource(@NonNull YoUsageSource source) {
        YoUsageTotals totals = bySource.get(source);
        return totals == null ? YoUsageTotals.EMPTY : totals;
    }

    @NonNull
    private static Map<String, YoUsageTotals> immutableSorted(
            @NonNull Map<String, YoUsageTotals> source) {
        if (source == null) {
            throw new IllegalArgumentException("breakdown is required");
        }
        if (source.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(new TreeMap<>(source)));
    }

    @NonNull
    private static Map<YoUsageSource, YoUsageTotals> immutableSources(
            @NonNull Map<YoUsageSource, YoUsageTotals> source) {
        if (source == null) {
            throw new IllegalArgumentException("breakdown is required");
        }
        if (source.isEmpty()) {
            return Map.of();
        }
        EnumMap<YoUsageSource, YoUsageTotals> copy = new EnumMap<>(YoUsageSource.class);
        copy.putAll(source);
        return Collections.unmodifiableMap(copy);
    }
}
