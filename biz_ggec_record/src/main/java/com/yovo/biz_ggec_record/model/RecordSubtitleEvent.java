package com.yovo.biz_ggec_record.model;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * ASR 字幕事件。
 */
public final class RecordSubtitleEvent {

    private final String text;
    private final boolean finalResult;

    public RecordSubtitleEvent(@NonNull String text, boolean finalResult) {
        this.text = Objects.requireNonNull(text, "text");
        this.finalResult = finalResult;
    }

    @NonNull
    public String text() {
        return text;
    }

    public boolean isFinalResult() {
        return finalResult;
    }
}
