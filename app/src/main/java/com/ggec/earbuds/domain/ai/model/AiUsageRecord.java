package com.ggec.earbuds.domain.ai.model;

import androidx.annotation.Nullable;

import java.math.BigDecimal;

/**
 * 一次请求的用量流水。这是一条只追加的账，不是界面模型：
 * 所以它带时间戳、平台、模型、耗时和结局，即使请求失败或没有用量也照样记一笔——
 * 否则“请求失败率”和“平均耗时”这类问题永远无从回答。
 *
 * <p>会话 id 可以为空：标题生成、模型测速这类后台请求不属于任何会话，
 * 强行编一个会让“按会话统计”出现幽灵条目。</p>
 */
public record AiUsageRecord(
        long timestampMillis,
        @Nullable String conversationId,
        String platformId,
        String modelId,
        int inputTokens,
        int outputTokens,
        int totalTokens,
        @Nullable BigDecimal cost,
        long elapsedMillis,
        AiUsageStatus status,
        @Nullable String failureReason) {

    public AiUsageRecord {
        platformId = platformId == null ? "" : platformId;
        modelId = modelId == null ? "" : modelId;
        inputTokens = Math.max(0, inputTokens);
        outputTokens = Math.max(0, outputTokens);
        totalTokens = totalTokens > 0 ? totalTokens : inputTokens + outputTokens;
        elapsedMillis = Math.max(0L, elapsedMillis);
        status = status == null ? AiUsageStatus.SUCCESS : status;
        conversationId = conversationId == null || conversationId.isBlank() ? null : conversationId;
        failureReason = failureReason == null || failureReason.isBlank() ? null : failureReason;
    }

    /**
     * 从一次请求的结果落账。{@code usage} 为空（平台没报用量）时令牌记 0、费用未知，
     * 记录本身仍然保留——失败和取消的请求同样占一行。
     */
    public static AiUsageRecord request(
            long timestampMillis,
            @Nullable String conversationId,
            String platformId,
            String modelId,
            @Nullable AiUsage usage,
            long elapsedMillis,
            AiUsageStatus status,
            @Nullable String failureReason) {
        return new AiUsageRecord(
                timestampMillis,
                conversationId,
                platformId,
                modelId,
                usage == null ? 0 : usage.inputTokens(),
                usage == null ? 0 : usage.outputTokens(),
                usage == null ? 0 : usage.totalTokens(),
                usage == null ? null : usage.cost(),
                elapsedMillis,
                status,
                failureReason);
    }

    public boolean success() {
        return status == AiUsageStatus.SUCCESS;
    }

    public boolean hasCost() {
        return cost != null;
    }
}
