package com.ggec.earbuds.ui.ai.settings;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

/**
 * 平台详情页的一次性副作用：弹提示，或者平台已被移除、关闭页面。
 */
public record AiPlatformEvent(Type type, @StringRes int message, @Nullable String argument) {

    public enum Type {
        TOAST,
        FINISH
    }

    public static AiPlatformEvent toast(@StringRes int message) {
        return new AiPlatformEvent(Type.TOAST, message, null);
    }

    public static AiPlatformEvent toast(@StringRes int message, String argument) {
        return new AiPlatformEvent(Type.TOAST, message, argument);
    }

    public static AiPlatformEvent finish() {
        return new AiPlatformEvent(Type.FINISH, 0, null);
    }
}
