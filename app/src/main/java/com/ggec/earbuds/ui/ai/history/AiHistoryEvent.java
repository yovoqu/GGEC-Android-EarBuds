package com.ggec.earbuds.ui.ai.history;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

/**
 * 历史面板只消费一次的副作用：提示语。旋转重建后不重放。
 */
public record AiHistoryEvent(@StringRes int message, @Nullable String argument) {

    public static AiHistoryEvent toast(@StringRes int message) {
        return new AiHistoryEvent(message, null);
    }

    public static AiHistoryEvent toast(@StringRes int message, @Nullable String argument) {
        return new AiHistoryEvent(message, argument);
    }
}
