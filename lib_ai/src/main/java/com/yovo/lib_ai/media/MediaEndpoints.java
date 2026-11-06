package com.yovo.lib_ai.media;

import androidx.annotation.NonNull;

import com.yovo.lib_ai.platform.YoServiceTarget;

/**
 * 媒体服务端点拼装。DashScope 原生走 {@code /api/v1/...}，OpenAI 走 {@code /v1/...}。
 */
final class MediaEndpoints {

    private static final String QWEN_VERSION = "/api/v1";
    private static final String OPENAI_VERSION = "/v1";

    private static final String IMAGE_QWEN_SUFFIX =
            "/services/aigc/multimodal-generation/generation";
    private static final String VIDEO_QWEN_SUFFIX =
            "/services/aigc/video-generation/video-synthesis";
    private static final String TASKS_SUFFIX = "/tasks/";
    private static final String OPENAI_IMAGES_SUFFIX = "/images/generations";

    private MediaEndpoints() {
    }

    @NonNull
    static String qwenImage(@NonNull String baseUrl) {
        return qwenService(baseUrl, IMAGE_QWEN_SUFFIX);
    }

    @NonNull
    static String qwenVideo(@NonNull String baseUrl) {
        return qwenService(baseUrl, VIDEO_QWEN_SUFFIX);
    }

    @NonNull
    static String qwenTask(@NonNull String baseUrl, @NonNull String taskId) {
        return qwenService(baseUrl, TASKS_SUFFIX + taskId);
    }

    @NonNull
    static String openAiImages(@NonNull String baseUrl) {
        String normalized = YoServiceTarget.normalizeBaseUrl(baseUrl);
        if (endsWithIgnoreCase(normalized, OPENAI_IMAGES_SUFFIX)) {
            return normalized;
        }
        if (endsWithIgnoreCase(normalized, OPENAI_VERSION)) {
            return normalized + OPENAI_IMAGES_SUFFIX;
        }
        return normalized + OPENAI_VERSION + OPENAI_IMAGES_SUFFIX;
    }

    @NonNull
    private static String qwenService(@NonNull String baseUrl, @NonNull String suffix) {
        String normalized = YoServiceTarget.normalizeBaseUrl(baseUrl);
        if (endsWithIgnoreCase(normalized, suffix)) {
            return normalized;
        }
        if (endsWithIgnoreCase(normalized, QWEN_VERSION)) {
            return normalized + suffix;
        }
        return normalized + QWEN_VERSION + suffix;
    }

    private static boolean endsWithIgnoreCase(@NonNull String value, @NonNull String suffix) {
        int offset = value.length() - suffix.length();
        return offset >= 0 && value.regionMatches(true, offset, suffix, 0, suffix.length());
    }
}
