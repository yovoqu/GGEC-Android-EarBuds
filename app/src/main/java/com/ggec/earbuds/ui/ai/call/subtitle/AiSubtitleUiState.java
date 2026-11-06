package com.ggec.earbuds.ui.ai.call.subtitle;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.Objects;

/**
 * 字幕卡片整体 UI 状态快照 (Immutable UI State)。
 *
 * <p>遵循响应式 MVVM 单向数据流，包含：
 * <ul>
 *   <li>{@link #items()}：多轮结构化字幕数据项；</li>
 *   <li>{@link #visible()}：字幕浮层显隐开关状态。</li>
 * </ul>
 * </p>
 */
public record AiSubtitleUiState(
        @NonNull List<AiSubtitleItem> items,
        boolean visible
) {
    public static final AiSubtitleUiState HIDDEN = new AiSubtitleUiState(List.of(), false);

    public AiSubtitleUiState {
        Objects.requireNonNull(items, "items");
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }
}
