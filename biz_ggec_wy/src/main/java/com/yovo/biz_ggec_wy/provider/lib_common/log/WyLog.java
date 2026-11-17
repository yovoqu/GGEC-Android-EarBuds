package com.yovo.biz_ggec_wy.provider.lib_common.log;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_common.log.YoLog;
import com.yovo.lib_common.log.YoLogBootstrap;

/**
 * WY 模块唯一日志门面，同时是 {@code lib_common.log} 的 Provider 接入点。
 */
public final class WyLog {

    private WyLog() {
    }

    /**
     * SDK 入口兜底初始化 YoLog（幂等）。
     */
    public static void ensureInitialized(@NonNull Context context) {
        YoLogBootstrap.ensureInitialized(context);
    }

    public static void v(@NonNull String tag, @NonNull String message) {
        YoLog.v(tag, message);
    }

    public static void d(@NonNull String tag, @NonNull String message) {
        YoLog.d(tag, message);
    }

    public static void d(@NonNull String tag, @NonNull String message,
                         @Nullable Throwable throwable) {
        YoLog.d(tag, message, throwable);
    }

    public static void i(@NonNull String tag, @NonNull String message) {
        YoLog.i(tag, message);
    }

    public static void i(@NonNull String tag, @NonNull String message,
                         @Nullable Throwable throwable) {
        YoLog.i(tag, message, throwable);
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
