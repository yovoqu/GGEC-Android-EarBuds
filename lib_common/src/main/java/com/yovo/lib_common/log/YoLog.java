package com.yovo.lib_common.log;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_common.log.internal.core.LogCore;
import com.yovo.lib_common.log.model.FileLogStatus;
import com.yovo.lib_common.log.model.InitResult;
import com.yovo.lib_common.log.model.LogConfig;
import com.yovo.lib_common.log.model.LogContext;
import com.yovo.lib_common.log.model.LogLevel;

import java.util.function.Supplier;

/**
 * 库内唯一通用日志门面：Logcat 打印 + 文件落盘（异步、滚动、保留、降级）。
 */
public final class YoLog {

    private YoLog() {
    }

    @NonNull
    public static InitResult init(@NonNull Context context, @NonNull LogConfig config) {
        return LogCore.init(context, config);
    }

    public static boolean isInitialized() {
        return LogCore.isInitialized();
    }

    public static boolean isLoggable(@NonNull LogLevel level) {
        return LogCore.isLoggable(level);
    }

    // ---- 基础 API ----

    public static void v(@NonNull String message) {
        LogCore.v(message);
    }

    public static void v(@NonNull String tag, @NonNull String message) {
        LogCore.v(tag, message);
    }

    public static void d(@NonNull String message) {
        LogCore.d(message);
    }

    public static void d(@NonNull String tag, @NonNull String message) {
        LogCore.d(tag, message);
    }

    public static void i(@NonNull String message) {
        LogCore.i(message);
    }

    public static void i(@NonNull String tag, @NonNull String message) {
        LogCore.i(tag, message);
    }

    public static void w(@NonNull String message) {
        LogCore.w(message);
    }

    public static void w(@NonNull String tag, @NonNull String message) {
        LogCore.w(tag, message);
    }

    public static void w(@NonNull String tag, @NonNull String message,
                         @Nullable Throwable throwable) {
        LogCore.w(tag, message, throwable);
    }

    public static void v(@NonNull String tag, @NonNull String message,
                         @Nullable Throwable throwable) {
        LogCore.v(tag, message, throwable);
    }

    public static void d(@NonNull String tag, @NonNull String message,
                         @Nullable Throwable throwable) {
        LogCore.d(tag, message, throwable);
    }

    public static void i(@NonNull String tag, @NonNull String message,
                         @Nullable Throwable throwable) {
        LogCore.i(tag, message, throwable);
    }

    public static void w(@NonNull String tag, @NonNull String format,
                         @NonNull Object... args) {
        LogCore.w(tag, format, args);
    }

    public static void e(@NonNull String message) {
        LogCore.e(message);
    }

    public static void e(@NonNull String tag, @NonNull String message) {
        LogCore.e(tag, message);
    }

    public static void e(@NonNull String tag, @NonNull String message,
                         @Nullable Throwable throwable) {
        LogCore.e(tag, message, throwable);
    }

    public static void e(@NonNull String tag, @NonNull String format,
                         @NonNull Object... args) {
        LogCore.e(tag, format, args);
    }

    public static void v(@NonNull String tag, @NonNull String format,
                         @NonNull Object... args) {
        LogCore.v(tag, format, args);
    }

    public static void d(@NonNull String tag, @NonNull String format,
                         @NonNull Object... args) {
        LogCore.d(tag, format, args);
    }

    public static void i(@NonNull String tag, @NonNull String format,
                         @NonNull Object... args) {
        LogCore.i(tag, format, args);
    }

    // ---- 懒求值 ----

    public static void d(@NonNull String tag, @NonNull Supplier<String> message) {
        LogCore.d(tag, message);
    }

    public static void e(@NonNull String tag,
                         @NonNull Supplier<String> message,
                         @Nullable Throwable throwable) {
        LogCore.e(tag, message, throwable);
    }

    // ---- 状态与生命周期 ----

    @NonNull
    public static FileLogStatus fileStatus() {
        return LogCore.fileStatus();
    }

    public static void flush() {
        LogCore.flush();
    }

    public static void shutdown() {
        LogCore.shutdown();
    }

    // ---- LogContext（类 MDC）----

    public static void putContext(@NonNull String key, @NonNull String value) {
        LogContext.put(key, value);
    }

    public static void removeContext(@NonNull String key) {
        LogContext.remove(key);
    }

    public static void clearContext() {
        LogContext.clear();
    }
}
