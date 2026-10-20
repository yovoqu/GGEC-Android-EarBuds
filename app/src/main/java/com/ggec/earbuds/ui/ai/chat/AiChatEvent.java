package com.ggec.earbuds.ui.ai.chat;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

/**
 * 只消费一次的界面副作用：旋转重建后不能重放。
 */
public record AiChatEvent(Type type, @StringRes int message, @Nullable String argument) {

    public enum Type {
        TOAST,
        TOAST_WITH_ARGUMENT
    }

    public static AiChatEvent toast(@StringRes int message) {
        return new AiChatEvent(Type.TOAST, message, null);
    }

    public static AiChatEvent toast(@StringRes int message, String argument) {
        return new AiChatEvent(Type.TOAST_WITH_ARGUMENT, message, argument);
    }
}
