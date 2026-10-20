package com.ggec.earbuds.ui.ai.settings;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

/**
 * 设置页的一次性副作用。
 */
public record AiSettingsEvent(Type type, @StringRes int message, @Nullable String argument) {

    public enum Type {
        TOAST,
        OPEN_PLATFORM
    }

    public static AiSettingsEvent toast(@StringRes int message) {
        return new AiSettingsEvent(Type.TOAST, message, null);
    }

    public static AiSettingsEvent openPlatform(String platformId) {
        return new AiSettingsEvent(Type.OPEN_PLATFORM, 0, platformId);
    }
}
