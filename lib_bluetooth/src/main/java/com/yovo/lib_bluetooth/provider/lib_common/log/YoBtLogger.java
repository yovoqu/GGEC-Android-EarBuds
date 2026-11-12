package com.yovo.lib_bluetooth.provider.lib_common.log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.content.Context;

import com.yovo.lib_common.log.YoLog;
import com.yovo.lib_common.log.YoLogBootstrap;

/**
 * 蓝牙模块唯一日志门面，同时是 {@code lib_common.log} 的 Provider 接入点。
 *
 * <p>模块内所有日志点统一调用本类；本类直接委托 {@code lib_common} 的 {@link YoLog}
 * （Logcat + 文件落盘：异步、滚动、保留、降级），不经端口/适配器等中间层。
 * 本模块只有 {@code provider/lib_common/log} 允许依赖 {@code com.yovo.lib_common.log}，
 * 其余包只使用本门面，日志级别与落盘策略由 {@code lib_common.log} 统一控制。</p>
 */
public final class YoBtLogger {

    private YoBtLogger() {
    }

    /**
     * SDK 入口兜底初始化 YoLog（幂等）：App 未初始化时用安全默认配置初始化一次；
     * 已初始化则 no-op，绝不重复初始化。
     */
    public static void ensureInitialized(@NonNull Context context) {
        YoLogBootstrap.ensureInitialized(context);
    }

    // ── 基础日志 ────────────────────────────────────────────────

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

    // ── 上下文（类 MDC）────────────────────────────────────────
    // 直接桥接 YoLog 的 LogContext，供 deviceId / connectionId / traceId 维度追踪。

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
