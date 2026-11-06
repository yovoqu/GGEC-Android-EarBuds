package com.yovo.lib_ai.speech;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 一个词级时间戳（OpenAI {@code timestamp_granularities=word} 或 diarized 的 segment.words）。
 */
public final class YoWord {

    @NonNull
    private final String word;
    private final double startSeconds;
    private final double endSeconds;
    @Nullable
    private final String speaker;

    public YoWord(
            @NonNull String word,
            double startSeconds,
            double endSeconds,
            @Nullable String speaker) {
        this.word = word;
        this.startSeconds = startSeconds;
        this.endSeconds = endSeconds;
        this.speaker = speaker;
    }

    @NonNull
    public String word() {
        return word;
    }

    public double startSeconds() {
        return startSeconds;
    }

    public double endSeconds() {
        return endSeconds;
    }

    /** 说话人标签；未做说话人分离时为 {@code null}。 */
    @Nullable
    public String speaker() {
        return speaker;
    }
}
