package com.yovo.lib_ai.realtime;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 把用户那一句音频转成文字。不配则服务端不回用户转写，通话页只能显示助手说了什么。
 */
public final class YoRealtimeTranscription {

    @NonNull
    private final String model;
    @Nullable
    private final String language;
    @Nullable
    private final String prompt;

    private YoRealtimeTranscription(
            @NonNull String model,
            @Nullable String language,
            @Nullable String prompt) {
        this.model = model;
        this.language = language;
        this.prompt = prompt;
    }

    @NonNull
    public static YoRealtimeTranscription of(@NonNull String model) {
        return of(model, null, null);
    }

    @NonNull
    public static YoRealtimeTranscription of(
            @NonNull String model,
            @Nullable String language,
            @Nullable String prompt) {
        if (model.isBlank()) {
            throw new IllegalArgumentException("transcription model is required");
        }
        return new YoRealtimeTranscription(model.trim(), blankToNull(language), blankToNull(prompt));
    }

    @NonNull
    public String model() {
        return model;
    }

    @Nullable
    public String language() {
        return language;
    }

    @Nullable
    public String prompt() {
        return prompt;
    }

    @Nullable
    private static String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
