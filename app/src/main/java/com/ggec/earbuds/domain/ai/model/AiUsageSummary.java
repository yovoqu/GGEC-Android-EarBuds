package com.ggec.earbuds.domain.ai.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

/**
 * 用量流水的聚合结果（纯函数产物，不落盘）。
 *
 * <p>为什么做成静态纯函数而不是“统计服务”：聚合只依赖传入的记录列表，没有时钟、
 * 没有存储，所以可以拿固定数据单测，界面也能按任意分组、任意时间段自己算，
 * 不必为每种视图再往领域层加一个服务。</p>
 *
 * <p>费用是 {@link BigDecimal}：按分累加的金额用 double 会产生看不见的误差。
 * 合计费用在没有记录报过钱时是 0，但 {@link #pricedRequests()} 会把这件事说清楚——
 * “一笔都没上报”和“上报了但都是 0 元”必须能分开，否则设置页会把未知金额显示成免费。</p>
 */
public record AiUsageSummary(
        int requests,
        long inputTokens,
        long outputTokens,
        long totalTokens,
        BigDecimal cost,
        int pricedRequests) {

    public AiUsageSummary {
        requests = Math.max(0, requests);
        pricedRequests = Math.max(0, pricedRequests);
        cost = cost == null ? BigDecimal.ZERO : cost;
    }

    public static AiUsageSummary empty() {
        return new AiUsageSummary(0, 0L, 0L, 0L, BigDecimal.ZERO, 0);
    }

    /**
     * 累加整份流水。成功、失败、取消都算一次请求——否则失败率永远算不出来。
     */
    public static AiUsageSummary summarize(List<AiUsageRecord> records) {
        AiUsageSummary summary = empty();
        for (AiUsageRecord record : records) {
            summary = summary.plus(record);
        }
        return summary;
    }

    /**
     * 只统计 {@code [fromMillis, toMillis)} 内的记录。左闭右开，相邻两段不会重复计数。
     */
    public static AiUsageSummary summarize(List<AiUsageRecord> records, long fromMillis, long toMillis) {
        return summarize(inRange(records, fromMillis, toMillis));
    }

    public static List<AiUsageRecord> inRange(List<AiUsageRecord> records, long fromMillis, long toMillis) {
        List<AiUsageRecord> kept = new ArrayList<>();
        for (AiUsageRecord record : records) {
            if (record.timestampMillis() >= fromMillis && record.timestampMillis() < toMillis) {
                kept.add(record);
            }
        }
        return kept;
    }

    public AiUsageSummary plus(AiUsageRecord record) {
        Objects.requireNonNull(record, "record");
        BigDecimal recordCost = record.cost();
        return new AiUsageSummary(
                requests + 1,
                inputTokens + record.inputTokens(),
                outputTokens + record.outputTokens(),
                totalTokens + record.totalTokens(),
                recordCost == null ? cost : cost.add(recordCost),
                pricedRequests + (recordCost == null ? 0 : 1));
    }

    public AiUsageSummary plus(AiUsageSummary other) {
        Objects.requireNonNull(other, "other");
        return new AiUsageSummary(
                requests + other.requests,
                inputTokens + other.inputTokens,
                outputTokens + other.outputTokens,
                totalTokens + other.totalTokens,
                cost.add(other.cost),
                pricedRequests + other.pricedRequests);
    }

    /**
     * 按平台分组。键是平台 id，排序固定，便于界面直接按顺序画。
     */
    public static Map<String, AiUsageSummary> byPlatform(List<AiUsageRecord> records) {
        Map<String, AiUsageSummary> grouped = new TreeMap<>();
        for (AiUsageRecord record : records) {
            grouped.merge(record.platformId(), empty().plus(record), AiUsageSummary::plus);
        }
        return grouped;
    }

    /**
     * 按会话分组。没有会话 id 的记录（标题生成、测速）不进入结果：
     * 它们不属于任何会话，硬挂到一个空 key 上只会污染统计。
     */
    public static Map<String, AiUsageSummary> byConversation(List<AiUsageRecord> records) {
        Map<String, AiUsageSummary> grouped = new TreeMap<>();
        for (AiUsageRecord record : records) {
            String conversationId = record.conversationId();
            if (conversationId == null) {
                continue;
            }
            grouped.merge(conversationId, empty().plus(record), AiUsageSummary::plus);
        }
        return grouped;
    }

    /**
     * 按固定宽度的桶分组的辅助：键是桶起点，桶宽由调用方给。
     * 这里只按绝对时间切分、不引入时区，本地零点怎么对齐由界面决定。
     */
    public static NavigableMap<Long, AiUsageSummary> byPeriod(List<AiUsageRecord> records, long bucketMillis) {
        if (bucketMillis <= 0L) {
            throw new IllegalArgumentException("bucketMillis must be positive");
        }
        NavigableMap<Long, AiUsageSummary> grouped = new TreeMap<>();
        for (AiUsageRecord record : records) {
            long bucket = Math.floorDiv(record.timestampMillis(), bucketMillis) * bucketMillis;
            grouped.merge(bucket, empty().plus(record), AiUsageSummary::plus);
        }
        return grouped;
    }

    /**
     * 是否有任何一条记录报过费用。为 false 时 {@link #cost()} 的 0 表示“未知”，不是免费。
     */
    public boolean hasPricedRequests() {
        return pricedRequests > 0;
    }
}
