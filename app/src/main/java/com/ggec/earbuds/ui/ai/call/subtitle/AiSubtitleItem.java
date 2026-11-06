package com.ggec.earbuds.ui.ai.call.subtitle;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * 单条字幕回合表现层数据模型 (Presentation Model)。
 *
 * <p>严格遵循 Clean Architecture / MVVM 分层规范：
 * 作为 UI 层的不可变值对象 (Immutable Value Object)，完全解耦领域实体与视图层绘制。
 */
public record AiSubtitleItem(
        @NonNull String id,
        @NonNull Speaker speaker,
        @NonNull String text,
        boolean isFinal
) {
    public enum Speaker {
        USER,
        AI
    }

    public AiSubtitleItem {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(speaker, "speaker");
        Objects.requireNonNull(text, "text");
    }

    public boolean isUser() {
        return speaker == Speaker.USER;
    }
}
