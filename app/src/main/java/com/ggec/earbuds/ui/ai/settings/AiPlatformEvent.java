package com.ggec.earbuds.ui.ai.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

/**
 * 平台详情页只消费一次的副作用。
 *
 * @param argument 需要拼进文案的参数（例如"已写入 N 个模型"里的 N）；没有就为 null
 * @param closed   为真表示这一页已经没有对象（平台被删），页面应当自己关掉
 */
public record AiPlatformEvent(@StringRes int message, @Nullable String argument, boolean closed) {

    public static AiPlatformEvent toast(@StringRes int message) {
        return new AiPlatformEvent(message, null, false);
    }

    public static AiPlatformEvent toast(@StringRes int message, @Nullable String argument) {
        return new AiPlatformEvent(message, argument, false);
    }

    public static AiPlatformEvent closed(@StringRes int message) {
        return new AiPlatformEvent(message, null, true);
    }
}
