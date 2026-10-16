package com.yovo.lib_ai.platform;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 一台主机的基址和请求头。头的含义由平台绑定决定，基础设施只原样送出。
 */
public final class YoServiceTarget {

    private final String baseUrl;
    private final Map<String, String> headers;

    public YoServiceTarget(@NonNull String baseUrl, @NonNull Map<String, String> headers) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
    }

    @NonNull
    public String baseUrl() {
        return baseUrl;
    }

    @NonNull
    public Map<String, String> headers() {
        return headers;
    }

    @NonNull
    @Override
    public String toString() {
        return "YoServiceTarget{baseUrl=" + baseUrl + "}";
    }

    @NonNull
    public static String normalizeBaseUrl(@NonNull String baseUrl) {
        Objects.requireNonNull(baseUrl, "baseUrl");
        String trimmed = baseUrl.trim();
        if (!trimmed.startsWith("https://") && !trimmed.startsWith("http://")) {
            throw new IllegalArgumentException("baseUrl must start with http:// or https://");
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    @NonNull
    public static String requireText(@Nullable String value, @NonNull String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
