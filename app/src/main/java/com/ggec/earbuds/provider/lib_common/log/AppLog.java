package com.ggec.earbuds.provider.lib_common.log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_common.log.YoLog;

/**
 * APP 内唯一的日志入口。
 *
 * <p>初始化单点是 {@link YoLogProvider}；本类只把 APP 的调用转成 {@code lib_common.log} 的
 * 分级日志，不持状态、不做格式化策略（滚动、保留、脱敏都在库内配置里）。</p>
 *
 * <p>{@code com.yovo.lib_common.log} 只允许出现在 {@code provider/lib_common/log/}；
 * APP 其它代码一律经本类打日志。</p>
 */
public final class AppLog {

    private AppLog() {
    }

    public static void v(@NonNull String tag, @NonNull String message) {
        YoLog.v(tag, message);
    }

    public static void d(@NonNull String tag, @NonNull String message) {
        YoLog.d(tag, message);
    }

    public static void i(@NonNull String tag, @NonNull String message) {
        YoLog.i(tag, message);
    }

    public static void w(@NonNull String tag, @NonNull String message) {
        YoLog.w(tag, message);
    }

    public static void w(@NonNull String tag, @NonNull String message,
                         @Nullable Throwable throwable) {
        YoLog.w(tag, message, throwable);
    }

    public static void e(@NonNull String tag, @NonNull String message) {
        YoLog.e(tag, message);
    }

    public static void e(@NonNull String tag, @NonNull String message,
                         @Nullable Throwable throwable) {
        YoLog.e(tag, message, throwable);
    }
}
