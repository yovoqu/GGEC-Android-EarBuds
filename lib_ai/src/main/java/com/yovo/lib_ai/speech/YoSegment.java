package com.yovo.lib_ai.speech;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * 一个分段（{@code verbose_json} 的 segments，或 diarized_json 的带 speaker 的段）。
 */
public final class YoSegment {

    @Nullable
    private final String id;
    private final double startSeconds;
    private final double endSeconds;
    @NonNull
    private final String text;
    @Nullable
    private final String speaker;
    @NonNull
    private final List<YoWord> words;

    public YoSegment(
            @Nullable String id,
            double startSeconds,
            double endSeconds,
            @NonNull String text,
            @Nullable String speaker,
            @NonNull List<YoWord> words) {
        this.id = id;
        this.startSeconds = startSeconds;
        this.endSeconds = endSeconds;
        this.text = text;
        this.speaker = speaker;
        this.words = List.copyOf(words);
    }

    @Nullable
    public String id() {
        return id;
    }

    public double startSeconds() {
        return startSeconds;
    }

    public double endSeconds() {
        return endSeconds;
    }

    @NonNull
    public String text() {
        return text;
    }

    /** 说话人标签；未做说话人分离时为 {@code null}。 */
    @Nullable
    public String speaker() {
        return speaker;
    }

    @NonNull
    public List<YoWord> words() {
        return words;
    }
}
