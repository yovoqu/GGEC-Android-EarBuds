package com.yovo.lib_bluetooth.provider.lib_common.log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_common.log.YoLog;
import com.yovo.lib_common.log.YoLogBootstrap;
import com.yovo.lib_bluetooth.spi.log.LogSink;

/**
 * 蓝牙模块唯一日志门面，同时是 {@code lib_common.log} 的 Provider 接入点。
 *
 * <p>模块内所有日志点统一调用本类；本类直接委托 {@code lib_common} 的 {@link YoLog}
 * （Logcat + 文件落盘：异步、滚动、保留、降级），不经端口/适配器等中间层。
 * 本模块只有 {@code provider/lib_common/log} 允许依赖 {@code com.yovo.lib_common.log}，
 * 其余包只使用本门面，日志级别与落盘策略由 {@code lib_common.log} 统一控制。</p>
 *
 * <p>{@link #SINK} 是 {@link LogSink}（SPI）的唯一实现实例：装配根把它注入诊断总线与运行时，
 * 实现层因此只依赖 SPI，不依赖本类所在的 provider 包。</p>
 */
public final class YoBtLogger {

    /** SPI 实现实例：由模块装配根注入，不含状态，可跨客户端复用。 */
    public static final LogSink SINK = new LogSink() {
        @Override
        public void v(@NonNull String tag, @NonNull String message) {
            YoBtLogger.v(tag, message);
        }

        @Override
        public void d(@NonNull String tag, @NonNull String message) {
            YoBtLogger.d(tag, message);
        }

        @Override
        public void d(@NonNull String tag, @NonNull String message, @Nullable Throwable throwable) {
            YoBtLogger.d(tag, message, throwable);
        }

        @Override
        public void i(@NonNull String tag, @NonNull String message) {
            YoBtLogger.i(tag, message);
        }

        @Override
        public void i(@NonNull String tag, @NonNull String message, @Nullable Throwable throwable) {
            YoBtLogger.i(tag, message, throwable);
        }

        @Override
        public void w(@NonNull String tag, @NonNull String message) {
            YoBtLogger.w(tag, message);
        }

        @Override
        public void w(@NonNull String tag, @NonNull String message, @Nullable Throwable throwable) {
            YoBtLogger.w(tag, message, throwable);
        }

        @Override
        public void e(@NonNull String tag, @NonNull String message) {
            YoBtLogger.e(tag, message);
        }

        @Override
        public void e(@NonNull String tag, @NonNull String message, @Nullable Throwable throwable) {
            YoBtLogger.e(tag, message, throwable);
        }
    };

    private YoBtLogger() {
    }

    /**
     * 断言宿主已初始化日志（lib_common 的 {@code YoLogBootstrap} 只提供“未初始化即报错”的入口）。
     *
     * <p>本模块不再代宿主初始化日志：宿主未初始化时这里直接抛出，错误随调用栈暴露，
     * 不做兜底初始化、也不缓存“已初始化”状态。</p>
     */
    public static void requireInitialized() {
        YoLogBootstrap.requireInitialized();
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
