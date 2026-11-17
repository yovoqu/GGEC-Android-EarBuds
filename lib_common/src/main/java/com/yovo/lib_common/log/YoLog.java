package com.yovo.lib_common.log;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_common.log.internal.core.LogCore;
import com.yovo.lib_common.log.model.FileLogStatus;
import com.yovo.lib_common.log.model.InitResult;
import com.yovo.lib_common.log.model.LogConfig;
import com.yovo.lib_common.log.model.LogLevel;

import java.util.function.Supplier;

/**
 * 库内唯一通用日志门面：Logcat 打印 + 文件落盘（异步、滚动、保留、降级）。
 *
 * <p>门面持有唯一的 {@link LogCore} 实例，日志管线/配置/崩溃 flush 安装状态与上下文存储
 * 都属于该实例，不再散落为静态可写字段。</p>
 */
public final class YoLog {

    private static final LogCore CORE = new LogCore();

    private YoLog() {
    }

    @NonNull
    public static InitResult init(@NonNull Context context, @NonNull LogConfig config) {
        return CORE.init(context, config);
    }

    /**
     * 只初始化一次：已初始化则返回 {@code false}，不重建日志管线。
     */
    public static boolean initOnce(@NonNull Context context, @NonNull LogConfig config) {
        return CORE.initOnce(context, config);
    }

    @Nullable
    public static InitResult lastResult() {
        return CORE.lastResult();
    }

    public static boolean isInitialized() {
        return CORE.isInitialized();
    }

    public static boolean isLoggable(@NonNull LogLevel level) {
        return CORE.isLoggable(level);
    }

    // ---- 基础 API ----

    public static void v(@NonNull String message) {
        CORE.v(message);
    }

    public static void v(@NonNull String tag, @NonNull String message) {
        CORE.v(tag, message);
    }

    public static void d(@NonNull String message) {
        CORE.d(message);
    }

    public static void d(@NonNull String tag, @NonNull String message) {
        CORE.d(tag, message);
    }

    public static void i(@NonNull String message) {
        CORE.i(message);
    }

    public static void i(@NonNull String tag, @NonNull String message) {
        CORE.i(tag, message);
    }

    public static void w(@NonNull String message) {
        CORE.w(message);
    }

    public static void w(@NonNull String tag, @NonNull String message) {
        CORE.w(tag, message);
    }

    public static void w(@NonNull String tag, @NonNull String message,
                         @Nullable Throwable throwable) {
        CORE.w(tag, message, throwable);
    }

    public static void v(@NonNull String tag, @NonNull String message,
                         @Nullable Throwable throwable) {
        CORE.v(tag, message, throwable);
    }

    public static void d(@NonNull String tag, @NonNull String message,
                         @Nullable Throwable throwable) {
        CORE.d(tag, message, throwable);
    }

    public static void i(@NonNull String tag, @NonNull String message,
                         @Nullable Throwable throwable) {
        CORE.i(tag, message, throwable);
    }

    public static void w(@NonNull String tag, @NonNull String format,
                         @NonNull Object... args) {
        CORE.w(tag, format, args);
    }

    public static void e(@NonNull String message) {
        CORE.e(message);
    }

    public static void e(@NonNull String tag, @NonNull String message) {
        CORE.e(tag, message);
    }

    public static void e(@NonNull String tag, @NonNull String message,
                         @Nullable Throwable throwable) {
        CORE.e(tag, message, throwable);
    }

    public static void e(@NonNull String tag, @NonNull String format,
                         @NonNull Object... args) {
        CORE.e(tag, format, args);
    }

    public static void v(@NonNull String tag, @NonNull String format,
                         @NonNull Object... args) {
        CORE.v(tag, format, args);
    }

    public static void d(@NonNull String tag, @NonNull String format,
                         @NonNull Object... args) {
        CORE.d(tag, format, args);
    }

    public static void i(@NonNull String tag, @NonNull String format,
                         @NonNull Object... args) {
        CORE.i(tag, format, args);
    }

    // ---- 懒求值 ----

    public static void d(@NonNull String tag, @NonNull Supplier<String> message) {
        CORE.d(tag, message);
    }

    public static void e(@NonNull String tag,
                         @NonNull Supplier<String> message,
                         @Nullable Throwable throwable) {
        CORE.e(tag, message, throwable);
    }

    // ---- 状态与生命周期 ----

    @NonNull
    public static FileLogStatus fileStatus() {
        return CORE.fileStatus();
    }

    public static void flush() {
        CORE.flush();
    }

    public static void shutdown() {
        CORE.shutdown();
    }

    // ---- LogContext（类 MDC）----

    public static void putContext(@NonNull String key, @NonNull String value) {
        CORE.putContext(key, value);
    }

    public static void removeContext(@NonNull String key) {
        CORE.removeContext(key);
    }

    public static void clearContext() {
        CORE.clearContext();
    }
}
