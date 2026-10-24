package com.ggec.earbuds.domain.ai.model;

import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用量面板的数据（USE-02…05）：某个范围内的请求数（含失败数）、输入/输出 token、费用（仅有回报时）、
 * 按模型前 5 ＋"其他"、按来源的请求数。纯函数，固定"现在"和时区即可单测。
 */
public record AiUsageReport(
        Range range,
        int requests,
        int failedRequests,
        long inputTokens,
        long outputTokens,
        @Nullable BigDecimal cost,
        List<ModelRow> models,
        @Nullable ModelRow others,
        Map<AiUsageSource, Integer> bySource) {

    public static final int TOP_MODELS = 5;

    public enum Range {
        TODAY,
        LAST_7_DAYS,
        LAST_30_DAYS,
        ALL
    }

    /** 一行"{平台} · {模型}"。其他行的 platformId/modelId 为空。 */
    public record ModelRow(String platformId, String modelId, int requests, long inputTokens, long outputTokens) {
        public long tokens() {
            return inputTokens + outputTokens;
        }
    }

    public AiUsageReport {
        models = List.copyOf(models);
        bySource = Map.copyOf(bySource);
    }

    public boolean empty() {
        return requests == 0;
    }

    /** USE-03：范围内至少一条记录带费用才显示。 */
    public boolean showCost() {
        return cost != null;
    }

    public int requests(AiUsageSource source) {
        return bySource.getOrDefault(source, 0);
    }

    /** 范围起点（含）。今天＝本地今天 0 点；近 7 天＝含今天的 7 个自然日；全部＝最早。 */
    public static long rangeStart(Range range, long nowMillis, ZoneId zone) {
        LocalDate today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate();
        return switch (range) {
            case TODAY -> today.atStartOfDay(zone).toInstant().toEpochMilli();
            case LAST_7_DAYS -> today.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli();
            case LAST_30_DAYS -> today.minusDays(29).atStartOfDay(zone).toInstant().toEpochMilli();
            case ALL -> Long.MIN_VALUE;
        };
    }

    public static AiUsageReport of(List<AiUsageRecord> all, Range range, long nowMillis, ZoneId zone) {
        long from = rangeStart(range, nowMillis, zone);
        int requests = 0;
        int failed = 0;
        long input = 0;
        long output = 0;
        BigDecimal cost = null;
        Map<String, long[]> byModel = new LinkedHashMap<>();
        Map<String, String[]> keys = new LinkedHashMap<>();
        Map<AiUsageSource, Integer> bySource = new EnumMap<>(AiUsageSource.class);
        for (AiUsageRecord record : all) {
            if (record.timestampMillis() < from || record.timestampMillis() > nowMillis) {
                continue;
            }
            requests++;
            if (record.status() == AiUsageStatus.FAILED) {
                failed++;
            }
            input += record.inputTokens();
            output += record.outputTokens();
            if (record.cost() != null) {
                cost = cost == null ? record.cost() : cost.add(record.cost());
            }
            String key = record.platformId() + "\n" + record.modelId();
            long[] sums = byModel.computeIfAbsent(key, k -> new long[3]);
            keys.putIfAbsent(key, new String[]{record.platformId(), record.modelId()});
            sums[0]++;
            sums[1] += record.inputTokens();
            sums[2] += record.outputTokens();
            bySource.merge(record.source(), 1, Integer::sum);
        }
        List<ModelRow> rows = new ArrayList<>();
        for (Map.Entry<String, long[]> entry : byModel.entrySet()) {
            String[] key = keys.get(entry.getKey());
            long[] sums = entry.getValue();
            rows.add(new ModelRow(key[0], key[1], (int) sums[0], sums[1], sums[2]));
        }
        // 按 token 降序；token 相同按请求数，再按原顺序（稳定排序）。
        rows.sort(Comparator.comparingLong(ModelRow::tokens).reversed()
                .thenComparing(Comparator.comparingInt(ModelRow::requests).reversed()));
        ModelRow others = null;
        if (rows.size() > TOP_MODELS) {
            int otherRequests = 0;
            long otherInput = 0;
            long otherOutput = 0;
            for (ModelRow row : rows.subList(TOP_MODELS, rows.size())) {
                otherRequests += row.requests();
                otherInput += row.inputTokens();
                otherOutput += row.outputTokens();
            }
            others = new ModelRow("", "", otherRequests, otherInput, otherOutput);
            rows = new ArrayList<>(rows.subList(0, TOP_MODELS));
        }
        return new AiUsageReport(range, requests, failed, input, output, cost, rows, others, bySource);
    }
}
