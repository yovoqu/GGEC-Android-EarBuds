package com.yovo.lib_ai.internal.common.diagnostics;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.content.Context;

import com.yovo.lib_common.log.YoLog;
import com.yovo.lib_common.log.YoLogBootstrap;

/**
 * lib_ai 唯一日志门面。
 *
 * <p>模块内所有日志点统一调用本类；本类直接委托 {@code lib_common} 的 {@link YoLog}
 * （Logcat + 文件落盘：异步、滚动、保留、降级），不设中间端口/适配类。
 * 本类是 lib_ai 中唯一允许直接依赖 {@code com.yovo.lib_common.log} 的类，
 * 作为 lib_ai 与 lib_common 之间的日志对接缝（Provider seam）。</p>
 */
public final class YoAiLogger {

    /** 模块级降噪开关：false 时本模块全部日志静默（仍需全局 YoLog 级别过滤）。 */
    private static volatile boolean enabled = true;

    private YoAiLogger() {
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * SDK 入口兜底初始化 YoLog（幂等）：未初始化才用安全默认配置初始化一次，绝不重复初始化。
     */
    public static void ensureInitialized(@NonNull Context context) {
        YoLogBootstrap.ensureInitialized(context);
    }

    public static void v(@NonNull String tag, @NonNull String message) {
        if (enabled) {
            YoLog.v(tag, message);
        }
    }

    public static void d(@NonNull String tag, @NonNull String message) {
        if (enabled) {
            YoLog.d(tag, message);
        }
    }

    public static void d(@NonNull String tag, @NonNull String message,
                         @Nullable Throwable throwable) {
        if (enabled) {
            YoLog.d(tag, message, throwable);
        }
    }

    public static void i(@NonNull String tag, @NonNull String message) {
        if (enabled) {
            YoLog.i(tag, message);
        }
    }

    public static void i(@NonNull String tag, @NonNull String message,
                         @Nullable Throwable throwable) {
        if (enabled) {
            YoLog.i(tag, message, throwable);
        }
    }

    public static void w(@NonNull String tag, @NonNull String message) {
        if (enabled) {
            YoLog.w(tag, message);
        }
    }

    public static void w(@NonNull String tag, @NonNull String message,
                         @Nullable Throwable throwable) {
        if (enabled) {
            YoLog.w(tag, message, throwable);
        }
    }

    public static void e(@NonNull String tag, @NonNull String message) {
        if (enabled) {
            YoLog.e(tag, message);
        }
    }

    public static void e(@NonNull String tag, @NonNull String message,
                         @Nullable Throwable throwable) {
        if (enabled) {
            YoLog.e(tag, message, throwable);
        }
    }

    // ── 上下文（类 MDC）────────────────────────────────────────

    public static void putContext(@NonNull String key, @NonNull String value) {
        YoLog.putContext(key, value);
    }

    public static void removeContext(@NonNull String key) {
        YoLog.removeContext(key);
    }

    public static void clearContext() {
        YoLog.clearContext();
    }
}
