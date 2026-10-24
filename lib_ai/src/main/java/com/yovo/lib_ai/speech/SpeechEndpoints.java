package com.yovo.lib_ai.speech;

import androidx.annotation.NonNull;

import com.yovo.lib_ai.platform.YoServiceTarget;

/**
 * 转写端点地址的拼装规则，单独成类以便被单测直接驱动（不经网络）。
 *
 * <p>规则只有一条主线：{@code baseUrl} 若已经以 {@code /v1} 结尾，就不再补一次。
 * 这条判断很必要——同一个平台配置在对话链路里可能是 {@code https://host}，
 * 在语音链路里用户可能已经写成 {@code https://host/v1}，两种写法都合法；
 * 不判断就会打出 {@code /v1/v1/audio/transcriptions}，服务端回 404，
 * 而 404 在界面上的解释是"模型不存在"，很难倒推回 URL。
 */
final class SpeechEndpoints {

    private static final String TRANSCRIPTIONS_PATH = "/audio/transcriptions";
    private static final String VERSION_SEGMENT = "/v1";

    private SpeechEndpoints() {
    }

    /**
     * 由平台基址得到转写端点。
     *
     * <ul>
     *   <li>{@code https://api.openai.com} → {@code https://api.openai.com/v1/audio/transcriptions}</li>
     *   <li>{@code https://api.openai.com/v1} → 同一个（不重复拼 /v1）</li>
     *   <li>{@code https://gw.example.com/openai/v1} → {@code .../openai/v1/audio/transcriptions}</li>
     *   <li>已经是完整端点（结尾是 {@code /audio/transcriptions}）→ 原样返回，便于调用方直接贴端点</li>
     * </ul>
     *
     * @throws IllegalArgumentException baseUrl 不是 http/https 地址
     */
    @NonNull
    static String transcriptionUrl(@NonNull String baseUrl) {
        String normalized = YoServiceTarget.normalizeBaseUrl(baseUrl);
        if (endsWithIgnoreCase(normalized, TRANSCRIPTIONS_PATH)) {
            return normalized;
        }
        if (endsWithIgnoreCase(normalized, VERSION_SEGMENT)) {
            return normalized + TRANSCRIPTIONS_PATH;
        }
        return normalized + VERSION_SEGMENT + TRANSCRIPTIONS_PATH;
    }

    private static boolean endsWithIgnoreCase(String value, String suffix) {
        int offset = value.length() - suffix.length();
        return offset >= 0 && value.regionMatches(true, offset, suffix, 0, suffix.length());
    }
}
