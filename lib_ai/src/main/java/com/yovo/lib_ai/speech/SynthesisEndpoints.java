package com.yovo.lib_ai.speech;

import androidx.annotation.NonNull;

import com.yovo.lib_ai.models.YoServiceEndpoints;
import com.yovo.lib_ai.platform.YoServiceTarget;

/**
 * 合成端点拼装。OpenAI 复用 {@link YoServiceEndpoints#speech(String)}；
 * Qwen 的 DashScope 原生路径单独在这里补版本段。
 */
final class SynthesisEndpoints {

    private static final String QWEN_VERSION = "/api/v1";
    private static final String QWEN_TTS_SUFFIX = "/services/audio/tts/SpeechSynthesizer";

    private SynthesisEndpoints() {
    }

    /** OpenAI {@code POST /v1/audio/speech}。 */
    @NonNull
    static String openAiSpeech(@NonNull String baseUrl) {
        return YoServiceEndpoints.speech(baseUrl);
    }

    /**
     * Qwen {@code POST /api/v1/services/audio/tts/SpeechSynthesizer}。
     * 基址已带 {@code /api/v1} 时不再重复补版本段。
     */
    @NonNull
    static String qwenSpeech(@NonNull String baseUrl) {
        String normalized = YoServiceTarget.normalizeBaseUrl(baseUrl);
        if (endsWithIgnoreCase(normalized, QWEN_TTS_SUFFIX)) {
            return normalized;
        }
        if (endsWithIgnoreCase(normalized, QWEN_VERSION)) {
            return normalized + QWEN_TTS_SUFFIX;
        }
        return normalized + QWEN_VERSION + QWEN_TTS_SUFFIX;
    }

    private static boolean endsWithIgnoreCase(@NonNull String value, @NonNull String suffix) {
        int offset = value.length() - suffix.length();
        return offset >= 0 && value.regionMatches(true, offset, suffix, 0, suffix.length());
    }
}
