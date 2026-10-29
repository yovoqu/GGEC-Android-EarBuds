package com.ggec.earbuds.ui.ai.settings;

import androidx.annotation.StringRes;

/**
 * 设置总览页只消费一次的副作用：提示语。旋转重建后不重放。
 */
public record AiSettingsEvent(@StringRes int message) {

    public static AiSettingsEvent toast(@StringRes int message) {
        return new AiSettingsEvent(message);
    }
}
