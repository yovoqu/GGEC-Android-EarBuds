package com.ggec.earbuds.ui.ai.settings;

import androidx.annotation.StringRes;

/**
 * 平台详情页只消费一次的副作用。
 *
 * @param closed 为真表示这一页已经没有对象（平台被删），页面应当自己关掉
 */
public record AiPlatformEvent(@StringRes int message, boolean closed) {

    public static AiPlatformEvent toast(@StringRes int message) {
        return new AiPlatformEvent(message, false);
    }

    public static AiPlatformEvent closed(@StringRes int message) {
        return new AiPlatformEvent(message, true);
    }
}
