package com.yovo.lib_ai.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.platform.YoServiceTarget;

import java.net.URLEncoder;

/**
 * 端点拼装。单独成类是为了能被单测直接驱动（不经网络），也让"补不补 /v1"的规则只有一份。
 *
 * <p>基址归一化复用 {@link YoServiceTarget#normalizeBaseUrl(String)}，这里只负责补版本段和路径：
 * <ul>
 *   <li>{@code https://api.openai.com} + chat → {@code https://api.openai.com/v1/chat/completions}</li>
 *   <li>{@code https://api.openai.com/v1} + chat → 同一个（不重复补 /v1）</li>
 *   <li>{@code https://gw.example.com/openai/v1} → {@code .../openai/v1/chat/completions}</li>
 *   <li>已经是该类完整端点（结尾就是 {@code /chat/completions}）→ 原样返回，
 *       方便调用方直接贴端点；贴了别的协议的完整端点不再改写，属于配置错误。</li>
 * </ul>
 *
 * <p>实时语音走 {@code wss://}，{@code https} → {@code wss}、{@code http} → {@code ws}。
 */
public final class YoServiceEndpoints {

    private static final String VERSION = "/v1";
    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";
    private static final String MESSAGES_PATH = "/messages";
    private static final String RESPONSES_PATH = "/responses";
    private static final String TRANSCRIPTIONS_PATH = "/audio/transcriptions";
    private static final String SPEECH_PATH = "/audio/speech";
    private static final String REALTIME_PATH = "/realtime";

    private YoServiceEndpoints() {
    }

    /**
     * 按服务种类拼端点。实时语音需要建连时就带模型名，其余服务模型名在请求体里，不用出现在 URL 上。
     *
     * @throws IllegalArgumentException baseUrl 不是 http/https，或实时语音缺模型名
     */
    @NonNull
    public static String endpoint(@NonNull String baseUrl,
                                  @NonNull YoServiceKind serviceKind,
                                  @Nullable String modelId) {
        return switch (serviceKind) {
            case CHAT_COMPLETIONS -> chatCompletions(baseUrl);
            case MESSAGES -> messages(baseUrl);
            case RESPONSES -> responses(baseUrl);
            case AUDIO_TRANSCRIBE -> transcriptions(baseUrl);
            case AUDIO_SPEECH -> speech(baseUrl);
            case REALTIME -> realtime(baseUrl, modelId);
        };
    }

    @NonNull
    public static String chatCompletions(@NonNull String baseUrl) {
        return withPath(baseUrl, CHAT_COMPLETIONS_PATH);
    }

    @NonNull
    public static String messages(@NonNull String baseUrl) {
        return withPath(baseUrl, MESSAGES_PATH);
    }

    @NonNull
    public static String responses(@NonNull String baseUrl) {
        return withPath(baseUrl, RESPONSES_PATH);
    }

    @NonNull
    public static String transcriptions(@NonNull String baseUrl) {
        return withPath(baseUrl, TRANSCRIPTIONS_PATH);
    }

    @NonNull
    public static String speech(@NonNull String baseUrl) {
        return withPath(baseUrl, SPEECH_PATH);
    }

    /**
     * {@code wss://host/v1/realtime?model=…}。模型名可能带 {@code :} 或 {@code /}（如 {@code qwen2.5:7b}），
     * 所以要做 query 转义——不转义的话服务端会把它当成路径的一部分。
     */
    @NonNull
    public static String realtime(@NonNull String baseUrl, @Nullable String modelId) {
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("model id is required for realtime");
        }
        String base = webSocketBase(YoServiceTarget.normalizeBaseUrl(baseUrl));
        String url;
        if (endsWithIgnoreCase(base, REALTIME_PATH)) {
            url = base;
        } else if (endsWithIgnoreCase(base, VERSION)) {
            url = base + REALTIME_PATH;
        } else {
            url = base + VERSION + REALTIME_PATH;
        }
        if (url.indexOf('?') >= 0) {
            return url;
        }
        return url + "?model=" + encodeQueryValue(modelId.trim());
    }

    /**
     * 用 {@code String} 版重载（Android 上 {@code URLEncoder.encode(String, Charset)} 要 API 33，
     * minSdk 24 走不到），失败时退回原值——模型名只会含 ASCII，不值得为它抛异常。
     */
    @NonNull
    private static String encodeQueryValue(@NonNull String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (java.io.UnsupportedEncodingException error) {
            return value;
        }
    }

    /**
     * 基址 + 版本段 + 路径。只补缺失的那一段，重复调用是幂等的。
     */
    @NonNull
    private static String withPath(@NonNull String baseUrl, @NonNull String path) {
        String normalized = YoServiceTarget.normalizeBaseUrl(baseUrl);
        if (endsWithIgnoreCase(normalized, path)) {
            return normalized;
        }
        if (endsWithIgnoreCase(normalized, VERSION)) {
            return normalized + path;
        }
        return normalized + VERSION + path;
    }

    @NonNull
    private static String webSocketBase(@NonNull String normalizedBaseUrl) {
        if (normalizedBaseUrl.regionMatches(true, 0, "https://", 0, 8)) {
            return "wss://" + normalizedBaseUrl.substring(8);
        }
        if (normalizedBaseUrl.regionMatches(true, 0, "http://", 0, 7)) {
            return "ws://" + normalizedBaseUrl.substring(7);
        }
        throw new IllegalArgumentException("baseUrl must start with http:// or https://");
    }

    private static boolean endsWithIgnoreCase(@NonNull String value, @NonNull String suffix) {
        int offset = value.length() - suffix.length();
        return offset >= 0 && value.regionMatches(true, offset, suffix, 0, suffix.length());
    }
}
