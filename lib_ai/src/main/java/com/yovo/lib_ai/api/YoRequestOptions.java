package com.yovo.lib_ai.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 单次请求的可选传输配置：截止时间、幂等键、额外请求头。
 *
 * <p>不放在平台配置里，因为超时/幂等键通常按业务调用而非按平台变化。</p>
 */
public final class YoRequestOptions {

    @Nullable
    private final Long timeoutMillis;
    @Nullable
    private final String idempotencyKey;
    private final Map<String, String> headers;

    private YoRequestOptions(Builder builder) {
        this.timeoutMillis = builder.timeoutMillis;
        this.idempotencyKey = trimToNull(builder.idempotencyKey);
        this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(builder.headers));
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    @NonNull
    public static YoRequestOptions none() {
        return builder().build();
    }

    @Nullable
    public Long timeoutMillis() {
        return timeoutMillis;
    }

    @Nullable
    public String idempotencyKey() {
        return idempotencyKey;
    }

    @NonNull
    public Map<String, String> headers() {
        return headers;
    }

    @NonNull
    private static String trimToNull(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public static final class Builder {
        private Long timeoutMillis;
        private String idempotencyKey;
        private final Map<String, String> headers = new LinkedHashMap<>();

        private Builder() {
        }

        @NonNull
        public Builder timeoutMillis(@Nullable Long timeoutMillis) {
            if (timeoutMillis != null && timeoutMillis <= 0) {
                throw new IllegalArgumentException("timeoutMillis must be > 0");
            }
            this.timeoutMillis = timeoutMillis;
            return this;
        }

        @NonNull
        public Builder idempotencyKey(@Nullable String idempotencyKey) {
            this.idempotencyKey = idempotencyKey;
            return this;
        }

        @NonNull
        public Builder header(@NonNull String name, @NonNull String value) {
            if (name.isBlank() || value == null) {
                throw new IllegalArgumentException("header name/value is required");
            }
            headers.put(name, value);
            return this;
        }

        @NonNull
        public YoRequestOptions build() {
            return new YoRequestOptions(this);
        }
    }
}
