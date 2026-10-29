package com.yovo.lib_ai.usage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * 一次调用的用量：谁、走了哪条链路、花了多少 token 和多少钱。
 *
 * <p>金额单独带币种，因为不同平台的计费币种不同，把 0.01 美元和 0.01 人民币加在一起没有意义。
 * 平台没回报金额时 {@code cost} 为 {@code null}，此时 {@code currency} 仍保留，表示"知道币种但不知道钱"。
 *
 * <p>平台、模型、币种缺省时统一记成 {@link #UNSPECIFIED}，账本按这个键归组，
 * 这样"没填"的记录不会从汇总里消失，总数才对得上。
 *
 * @param platformId              平台 id，缺省为 {@link #UNSPECIFIED}
 * @param model                   请求里的 model 字段原文，缺省为 {@link #UNSPECIFIED}
 * @param source                  产生这条记录的链路
 * @param inputTokens             输入 token
 * @param outputTokens            输出 token
 * @param totalTokens             总 token；平台没单独给时等于输入加输出
 * @param cachedInputTokens       命中缓存的输入 token，平台没给为 {@code null}
 * @param cacheCreationInputTokens 写入缓存的输入 token，平台没给为 {@code null}
 * @param cost                    金额，平台没回报为 {@code null}
 * @param currency                币种（ISO 4217 大写），缺省为 {@link #UNSPECIFIED}
 * @param atMillis                发生时刻（epoch 毫秒）
 */
public record YoUsageRecord(
        @NonNull String platformId,
        @NonNull String model,
        @NonNull YoUsageSource source,
        int inputTokens,
        int outputTokens,
        int totalTokens,
        @Nullable Integer cachedInputTokens,
        @Nullable Integer cacheCreationInputTokens,
        @Nullable BigDecimal cost,
        @NonNull String currency,
        long atMillis) {

    /** 平台、模型、币种缺失时的统一键。 */
    public static final String UNSPECIFIED = "unspecified";

    public YoUsageRecord {
        if (source == null) {
            throw new IllegalArgumentException("source is required");
        }
        if (inputTokens < 0 || outputTokens < 0 || totalTokens < 0) {
            throw new IllegalArgumentException("token counts must not be negative");
        }
        if (atMillis < 0) {
            throw new IllegalArgumentException("atMillis must not be negative");
        }
        if (cost != null && cost.signum() < 0) {
            throw new IllegalArgumentException("cost must not be negative");
        }
        platformId = normalize(platformId);
        model = normalize(model);
        currency = normalizeCurrency(currency);
    }

    /** 平台有没有回报金额。为 {@code false} 时只统计 token，不计入任何币种的合计。 */
    public boolean hasCost() {
        return cost != null;
    }

    @NonNull
    private static String normalize(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return UNSPECIFIED;
        }
        return value.trim();
    }

    @NonNull
    private static String normalizeCurrency(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return UNSPECIFIED;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String platformId;
        private String model;
        private YoUsageSource source = YoUsageSource.OTHER;
        private int inputTokens;
        private int outputTokens;
        private Integer totalTokens;
        private Integer cachedInputTokens;
        private Integer cacheCreationInputTokens;
        private BigDecimal cost;
        private String currency;
        private Long atMillis;

        private Builder() {
        }

        @NonNull
        public Builder platformId(@Nullable String platformId) {
            this.platformId = platformId;
            return this;
        }

        @NonNull
        public Builder model(@Nullable String model) {
            this.model = model;
            return this;
        }

        @NonNull
        public Builder source(@NonNull YoUsageSource source) {
            this.source = source;
            return this;
        }

        @NonNull
        public Builder tokens(int inputTokens, int outputTokens) {
            this.inputTokens = inputTokens;
            this.outputTokens = outputTokens;
            return this;
        }

        /** 平台单独给了总 token 时用这个；不给则按输入加输出算。 */
        @NonNull
        public Builder totalTokens(@Nullable Integer totalTokens) {
            this.totalTokens = totalTokens;
            return this;
        }

        @NonNull
        public Builder cachedInputTokens(@Nullable Integer cachedInputTokens) {
            this.cachedInputTokens = cachedInputTokens;
            return this;
        }

        @NonNull
        public Builder cacheCreationInputTokens(@Nullable Integer cacheCreationInputTokens) {
            this.cacheCreationInputTokens = cacheCreationInputTokens;
            return this;
        }

        @NonNull
        public Builder cost(@Nullable BigDecimal cost) {
            this.cost = cost;
            return this;
        }

        @NonNull
        public Builder currency(@Nullable String currency) {
            this.currency = currency;
            return this;
        }

        @NonNull
        public Builder atMillis(long atMillis) {
            this.atMillis = atMillis;
            return this;
        }

        @NonNull
        public YoUsageRecord build() {
            int total = totalTokens != null ? totalTokens : inputTokens + outputTokens;
            long at = atMillis != null ? atMillis : System.currentTimeMillis();
            return new YoUsageRecord(platformId, model, source, inputTokens, outputTokens, total,
                    cachedInputTokens, cacheCreationInputTokens, cost, currency, at);
        }
    }
}
