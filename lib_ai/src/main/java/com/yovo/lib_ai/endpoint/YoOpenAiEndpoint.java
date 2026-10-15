package com.yovo.lib_ai.endpoint;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * OpenAI Chat Completions 与 Responses 共用的主机和鉴权。
 * 密钥只出现在请求头里，{@link #toString()} 不打印密钥。
 */
public final class YoOpenAiEndpoint {

    private final String baseUrl;
    private final String apiKey;
    @Nullable
    private final String organization;
    @Nullable
    private final String project;

    private YoOpenAiEndpoint(
            String baseUrl,
            String apiKey,
            @Nullable String organization,
            @Nullable String project) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.organization = organization;
        this.project = project;
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    @NonNull
    public String baseUrl() {
        return baseUrl;
    }

    @NonNull
    public Map<String, String> authHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + apiKey);
        if (organization != null) {
            headers.put("OpenAI-Organization", organization);
        }
        if (project != null) {
            headers.put("OpenAI-Project", project);
        }
        return headers;
    }

    @NonNull
    @Override
    public String toString() {
        return "YoOpenAiEndpoint{baseUrl=" + baseUrl + ", apiKey=redacted}";
    }

    public static final class Builder {
        private String baseUrl = "https://api.openai.com";
        private String apiKey;
        private String organization;
        private String project;

        @NonNull
        public Builder baseUrl(@NonNull String baseUrl) {
            this.baseUrl = normalizeBaseUrl(baseUrl);
            return this;
        }

        @NonNull
        public Builder apiKey(@NonNull String apiKey) {
            this.apiKey = requireText(apiKey, "apiKey");
            return this;
        }

        @NonNull
        public Builder organization(@Nullable String organization) {
            this.organization = blankToNull(organization);
            return this;
        }

        @NonNull
        public Builder project(@Nullable String project) {
            this.project = blankToNull(project);
            return this;
        }

        @NonNull
        public YoOpenAiEndpoint build() {
            if (apiKey == null) {
                throw new IllegalArgumentException("apiKey is required");
            }
            return new YoOpenAiEndpoint(normalizeBaseUrl(baseUrl), apiKey, organization, project);
        }
    }

    @NonNull
    static String normalizeBaseUrl(@NonNull String baseUrl) {
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
    static String requireText(@Nullable String value, @NonNull String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    @Nullable
    static String blankToNull(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
