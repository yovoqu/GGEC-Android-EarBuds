package com.yovo.lib_ai.platform;

import androidx.annotation.NonNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Anthropic Messages 的标准请求头。
 */
public final class AnthropicHeaders {

    private AnthropicHeaders() {
    }

    @NonNull
    public static YoServiceTarget target(
            @NonNull String baseUrl,
            @NonNull String apiKey,
            @NonNull String version,
            @NonNull List<String> betas) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("x-api-key", YoServiceTarget.requireText(apiKey, "apiKey"));
        headers.put("anthropic-version", YoServiceTarget.requireText(version, "version"));
        if (!betas.isEmpty()) {
            headers.put("anthropic-beta", String.join(",", betas));
        }
        return new YoServiceTarget(baseUrl, headers);
    }
}
