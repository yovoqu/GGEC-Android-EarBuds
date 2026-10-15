package com.yovo.lib_ai.endpoint;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Anthropic Messages 的主机、密钥和 {@code anthropic-version}。
 */
public final class YoAnthropicEndpoint {

    private final String baseUrl;
    private final String apiKey;
    private final String version;
    private final List<String> betas;

    private YoAnthropicEndpoint(String baseUrl, String apiKey, String version, List<String> betas) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.version = version;
        this.betas = betas;
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
        headers.put("x-api-key", apiKey);
        headers.put("anthropic-version", version);
        if (!betas.isEmpty()) {
            headers.put("anthropic-beta", String.join(",", betas));
        }
        return headers;
    }

    @NonNull
    @Override
    public String toString() {
        return "YoAnthropicEndpoint{baseUrl=" + baseUrl + ", apiKey=redacted, version=" + version + "}";
    }

    public static final class Builder {
        private String baseUrl = "https://api.anthropic.com";
        private String apiKey;
        private String version = "2023-06-01";
        private final List<String> betas = new ArrayList<>();

        @NonNull
        public Builder baseUrl(@NonNull String baseUrl) {
            this.baseUrl = YoOpenAiEndpoint.normalizeBaseUrl(baseUrl);
            return this;
        }

        @NonNull
        public Builder apiKey(@NonNull String apiKey) {
            this.apiKey = YoOpenAiEndpoint.requireText(apiKey, "apiKey");
            return this;
        }

        @NonNull
        public Builder version(@NonNull String version) {
            this.version = YoOpenAiEndpoint.requireText(version, "version");
            return this;
        }

        @NonNull
        public Builder addBeta(@NonNull String beta) {
            betas.add(YoOpenAiEndpoint.requireText(beta, "beta"));
            return this;
        }

        @NonNull
        public YoAnthropicEndpoint build() {
            if (apiKey == null) {
                throw new IllegalArgumentException("apiKey is required");
            }
            return new YoAnthropicEndpoint(
                    YoOpenAiEndpoint.normalizeBaseUrl(baseUrl),
                    apiKey,
                    version,
                    List.copyOf(betas));
        }
    }
}
