package com.yovo.lib_ai.probe;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.api.YoProtocol;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 一次测速要做的事：测哪些模型、走哪条协议、同时发几个、每个等多久。
 */
public final class YoProbePlan {

    public static final int MAX_MODELS = 64;
    public static final int MAX_CONCURRENCY = 8;
    public static final long MIN_TIMEOUT_MILLIS = 1_000L;
    public static final long MAX_TIMEOUT_MILLIS = 120_000L;
    public static final String DEFAULT_PROMPT = "Reply with the single word: pong";

    @Nullable
    private final YoProtocol protocol;
    private final List<String> models;
    private final int concurrency;
    private final long timeoutMillis;
    private final String prompt;
    private final int maxOutputTokens;
    private final boolean firstTokenOnly;

    private YoProbePlan(Builder builder) {
        this.protocol = builder.protocol;
        this.models = List.copyOf(builder.models);
        this.concurrency = builder.concurrency;
        this.timeoutMillis = builder.timeoutMillis;
        this.prompt = builder.prompt;
        this.maxOutputTokens = builder.maxOutputTokens;
        this.firstTokenOnly = builder.firstTokenOnly;
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 为空表示使用平台的默认协议。
     */
    @Nullable
    public YoProtocol protocol() {
        return protocol;
    }

    @NonNull
    public List<String> models() {
        return models;
    }

    public int concurrency() {
        return concurrency;
    }

    public long timeoutMillis() {
        return timeoutMillis;
    }

    @NonNull
    public String prompt() {
        return prompt;
    }

    public int maxOutputTokens() {
        return maxOutputTokens;
    }

    /**
     * 只测首字：收到第一个输出片段（正文或思考过程）就记结果并主动断开，不等回答说完。
     * 此时结果的总耗时等于首字耗时，也没有输出 token 数。
     */
    public boolean firstTokenOnly() {
        return firstTokenOnly;
    }

    public static final class Builder {
        private YoProtocol protocol;
        private final Set<String> models = new LinkedHashSet<>();
        private int concurrency = 3;
        private long timeoutMillis = 20_000L;
        private String prompt = DEFAULT_PROMPT;
        private int maxOutputTokens = 16;
        private boolean firstTokenOnly;

        @NonNull
        public Builder protocol(@Nullable YoProtocol protocol) {
            this.protocol = protocol;
            return this;
        }

        @NonNull
        public Builder addModel(@NonNull String model) {
            if (model.isBlank()) {
                throw new IllegalArgumentException("model is required");
            }
            models.add(model.trim());
            return this;
        }

        @NonNull
        public Builder models(@NonNull List<String> models) {
            this.models.clear();
            for (String model : models) {
                addModel(model);
            }
            return this;
        }

        @NonNull
        public Builder concurrency(int concurrency) {
            if (concurrency < 1 || concurrency > MAX_CONCURRENCY) {
                throw new IllegalArgumentException("concurrency must be from 1 to " + MAX_CONCURRENCY);
            }
            this.concurrency = concurrency;
            return this;
        }

        @NonNull
        public Builder timeoutMillis(long timeoutMillis) {
            if (timeoutMillis < MIN_TIMEOUT_MILLIS || timeoutMillis > MAX_TIMEOUT_MILLIS) {
                throw new IllegalArgumentException("timeoutMillis must be from "
                        + MIN_TIMEOUT_MILLIS + " to " + MAX_TIMEOUT_MILLIS);
            }
            this.timeoutMillis = timeoutMillis;
            return this;
        }

        @NonNull
        public Builder prompt(@NonNull String prompt) {
            if (prompt.isBlank()) {
                throw new IllegalArgumentException("prompt is required");
            }
            this.prompt = prompt;
            return this;
        }

        @NonNull
        public Builder maxOutputTokens(int maxOutputTokens) {
            if (maxOutputTokens < 1) {
                throw new IllegalArgumentException("maxOutputTokens must be positive");
            }
            this.maxOutputTokens = maxOutputTokens;
            return this;
        }

        @NonNull
        public Builder firstTokenOnly(boolean firstTokenOnly) {
            this.firstTokenOnly = firstTokenOnly;
            return this;
        }

        @NonNull
        public YoProbePlan build() {
            if (models.isEmpty()) {
                throw new IllegalArgumentException("at least one model is required");
            }
            if (models.size() > MAX_MODELS) {
                throw new IllegalArgumentException("at most " + MAX_MODELS + " models per probe");
            }
            return new YoProbePlan(this);
        }
    }
}
