package com.yovo.lib_common.log.internal.core;

import android.content.Context;
import android.os.Process;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_common.log.internal.access.LogTargetSelector;
import com.yovo.lib_common.log.internal.async.AsyncLogWriter;
import com.yovo.lib_common.log.internal.crash.CrashFlushInstaller;
import com.yovo.lib_common.log.internal.intercept.RateLimitInterceptor;
import com.yovo.lib_common.log.internal.intercept.RedactingInterceptor;
import com.yovo.lib_common.log.internal.lifecycle.LogLifecycleBinder;
import com.yovo.lib_common.log.internal.pipeline.LogPipeline;
import com.yovo.lib_common.log.internal.sink.FileSink;
import com.yovo.lib_common.log.internal.sink.LogcatSink;
import com.yovo.lib_common.log.model.FileLogReason;
import com.yovo.lib_common.log.model.FileLogStatus;
import com.yovo.lib_common.log.model.InitResult;
import com.yovo.lib_common.log.model.LogConfig;
import com.yovo.lib_common.log.model.LogContext;
import com.yovo.lib_common.log.model.LogLevel;
import com.yovo.lib_common.log.model.LogInterceptor;
import com.yovo.lib_common.log.model.LogRecord;
import com.yovo.lib_common.log.model.LogSink;
import com.yovo.lib_common.log.YoLog;
import com.yovo.lib_common.system.file.model.PathTarget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * YoLog 核心：init/shutdown 互斥、记录构建、目标选择、降级与未初始化行为。
 */
@SuppressWarnings("deprecation")
public final class LogCore {

    private static final Object LOCK = new Object();

    private static final LogcatSink UNINITIALIZED_SINK = new LogcatSink(LogLevel.DEBUG);

    @Nullable
    private static volatile LogPipeline pipeline;

    @Nullable
    private static volatile LogConfig config;

    private static volatile boolean crashFlushInstalled;

    private static volatile boolean shutDown;

    private LogCore() {
    }

    @NonNull
    public static InitResult init(@NonNull Context context, @NonNull LogConfig newConfig) {
        synchronized (LOCK) {
            shutDown = false;
            if (pipeline != null) {
                shutdownLocked();
            }
            LogTargetSelector.Selection selection =
                    LogTargetSelector.select(context, newConfig);

            List<com.yovo.lib_common.log.model.LogInterceptor> interceptors =
                    buildInterceptors(newConfig);
            List<LogSink> sinks = new ArrayList<>();
            FileSink fileSink = null;
            AsyncLogWriter writer = null;
            try {
                if (selection.target() != null) {
                    fileSink = new FileSink(
                            context,
                            selection.target(),
                            newConfig,
                            processSuffix(newConfig),
                            selection.usingFallback());
                    fileSink.init();
                    sinks.add(fileSink);
                }

                writer = selection.target() != null
                        ? new AsyncLogWriter(
                        newConfig.queueCapacity(), sinks, newConfig.urgentFlushLevels())
                        : null;
                LogcatSink logcatSink = newConfig.enableLogcat()
                        ? new LogcatSink(newConfig.logcatMinLevel())
                        : null;

                pipeline = new LogPipeline(
                        newConfig,
                        logcatSink,
                        fileSink,
                        writer,
                        selection.target() == null
                                ? FileLogStatus.disabled(
                                selection.reason(), selection.detail())
                                : null,
                        interceptors);
            } catch (RuntimeException e) {
                if (writer != null) {
                    writer.shutdown();
                } else if (fileSink != null) {
                    fileSink.close();
                }
                pipeline = null;
                throw e;
            }
            config = newConfig;

            if (newConfig.flushOnBackground()) {
                LogLifecycleBinder.install(LogCore::flush);
            }
            if (newConfig.installCrashFlush()) {
                if (!crashFlushInstalled) {
                    CrashFlushInstaller.install((thread, cause) -> {
                        log(LogLevel.ASSERT, "YoLog", "process crash: " + cause, cause);
                        flushCrash();
                    });
                    crashFlushInstalled = true;
                }
            } else {
                CrashFlushInstaller.uninstall();
                crashFlushInstalled = false;
            }

            FileLogStatus status = fileSink != null ? fileSink.status() : null;
            boolean fileEnabled = status != null && status.enabled();
            String disabledReason = fileEnabled
                    ? null
                    : (status != null ? status.detail() : selection.detail());
            YoLog.i("YoLog", "YoLog 已初始化：logcat=" + newConfig.enableLogcat()
                    + "，文件日志=" + fileEnabled
                    + (fileEnabled ? "，目录=" + selection.target()
                    : "，原因=" + disabledReason));
            return new InitResult(
                    newConfig.enableLogcat(),
                    fileEnabled,
                    fileEnabled ? selection.target() : null,
                    disabledReason);
        }
    }

    public static boolean isInitialized() {
        return pipeline != null;
    }

    public static boolean isLoggable(@NonNull LogLevel level) {
        LogConfig current = config;
        LogLevel min = current != null ? current.minLevel() : LogLevel.DEBUG;
        return level.isEnabled(min);
    }

    public static void v(@NonNull String message) {
        log(LogLevel.VERBOSE, tag(), message, null);
    }

    public static void v(@NonNull String tag, @NonNull String message) {
        log(LogLevel.VERBOSE, tag, message, null);
    }

    public static void d(@NonNull String message) {
        log(LogLevel.DEBUG, tag(), message, null);
    }

    public static void d(@NonNull String tag, @NonNull String message) {
        log(LogLevel.DEBUG, tag, message, null);
    }

    public static void i(@NonNull String message) {
        log(LogLevel.INFO, tag(), message, null);
    }

    public static void i(@NonNull String tag, @NonNull String message) {
        log(LogLevel.INFO, tag, message, null);
    }

    public static void w(@NonNull String message) {
        log(LogLevel.WARN, tag(), message, null);
    }

    public static void w(@NonNull String tag, @NonNull String message) {
        log(LogLevel.WARN, tag, message, null);
    }

    public static void w(@NonNull String tag, @NonNull String message,
                         @Nullable Throwable throwable) {
        log(LogLevel.WARN, tag, message, throwable);
    }

    public static void v(@NonNull String tag, @NonNull String message,
                         @Nullable Throwable throwable) {
        log(LogLevel.VERBOSE, tag, message, throwable);
    }

    public static void d(@NonNull String tag, @NonNull String message,
                         @Nullable Throwable throwable) {
        log(LogLevel.DEBUG, tag, message, throwable);
    }

    public static void i(@NonNull String tag, @NonNull String message,
                         @Nullable Throwable throwable) {
        log(LogLevel.INFO, tag, message, throwable);
    }

    public static void w(@NonNull String tag, @NonNull String format,
                         @NonNull Object... args) {
        if (!isLoggable(LogLevel.WARN)) {
            return;
        }
        log(LogLevel.WARN, tag, String.format(Locale.US, format, args), null);
    }

    public static void e(@NonNull String message) {
        log(LogLevel.ERROR, tag(), message, null);
    }

    public static void e(@NonNull String tag, @NonNull String message) {
        log(LogLevel.ERROR, tag, message, null);
    }

    public static void e(@NonNull String tag, @NonNull String message,
                         @Nullable Throwable throwable) {
        log(LogLevel.ERROR, tag, message, throwable);
    }

    public static void e(@NonNull String tag, @NonNull String format,
                         @NonNull Object... args) {
        if (!isLoggable(LogLevel.ERROR)) {
            return;
        }
        log(LogLevel.ERROR, tag, String.format(Locale.US, format, args), null);
    }

    public static void v(@NonNull String tag, @NonNull String format,
                         @NonNull Object... args) {
        if (!isLoggable(LogLevel.VERBOSE)) {
            return;
        }
        log(LogLevel.VERBOSE, tag, String.format(Locale.US, format, args), null);
    }

    public static void d(@NonNull String tag, @NonNull String format,
                         @NonNull Object... args) {
        if (!isLoggable(LogLevel.DEBUG)) {
            return;
        }
        log(LogLevel.DEBUG, tag, String.format(Locale.US, format, args), null);
    }

    public static void i(@NonNull String tag, @NonNull String format,
                         @NonNull Object... args) {
        if (!isLoggable(LogLevel.INFO)) {
            return;
        }
        log(LogLevel.INFO, tag, String.format(Locale.US, format, args), null);
    }

    public static void d(@NonNull String tag, @NonNull Supplier<String> message) {
        lazy(LogLevel.DEBUG, tag, message, null);
    }

    public static void e(@NonNull String tag,
                         @NonNull Supplier<String> message,
                         @Nullable Throwable throwable) {
        lazy(LogLevel.ERROR, tag, message, throwable);
    }

    public static void log(@NonNull LogLevel level,
                           @NonNull String tag,
                           @NonNull String message,
                           @Nullable Throwable throwable) {
        if (!isLoggable(level)) {
            return;
        }
        LogConfig current = config;
        boolean captureThread = current == null || current.captureThread();
        boolean captureContext = current == null || current.captureContext();
        LogRecord record = new LogRecord(
                System.currentTimeMillis(),
                level,
                tag,
                message,
                captureThread ? Thread.currentThread().getName() : "",
                captureThread ? Thread.currentThread().getId() : 0L,
                throwable,
                captureContext ? LogContext.snapshot() : Collections.emptyMap());
        dispatch(record);
    }

    private static void lazy(@NonNull LogLevel level,
                             @NonNull String tag,
                             @NonNull Supplier<String> message,
                             @Nullable Throwable throwable) {
        if (!isLoggable(level)) {
            return;
        }
        String value;
        try {
            value = message.get();
        } catch (RuntimeException e) {
            value = "lazy message evaluation failed: " + e;
        }
        log(level, tag, value == null ? "null" : value, throwable);
    }

    @NonNull
    public static FileLogStatus fileStatus() {
        LogPipeline current = pipeline;
        if (current == null) {
            return shutDown
                    ? FileLogStatus.disabled(
                            FileLogReason.SHUTDOWN, "YoLog shut down")
                    : FileLogStatus.disabled(
                            FileLogReason.NOT_REQUESTED, "YoLog not initialized");
        }
        return current.fileStatus();
    }

    public static void flush() {
        LogPipeline current = pipeline;
        if (current != null) {
            current.flush(2000L);
        }
    }

    public static void flush(long timeoutMs) {
        LogPipeline current = pipeline;
        if (current != null) {
            current.flush(timeoutMs);
        }
    }

    private static void flushCrash() {
        LogConfig current = config;
        flush(current != null ? current.crashFlushTimeoutMs() : 2000L);
    }

    public static void shutdown() {
        synchronized (LOCK) {
            shutdownLocked();
        }
    }

    @NonNull
    private static String tag() {
        LogConfig current = config;
        return current != null ? current.defaultTag() : "YoLog";
    }

    private static void dispatch(@NonNull LogRecord record) {
        LogPipeline current = pipeline;
        if (current != null) {
            current.log(record);
        } else {
            UNINITIALIZED_SINK.write(record);
        }
    }

    private static void shutdownLocked() {
        LogPipeline current = pipeline;
        pipeline = null;
        config = null;
        CrashFlushInstaller.uninstall();
        crashFlushInstalled = false;
        LogLifecycleBinder.uninstall();
        shutDown = true;
        if (current != null) {
            current.shutdown();
        }
    }

    @NonNull
    private static List<LogInterceptor> buildInterceptors(@NonNull LogConfig cfg) {
        List<LogInterceptor> list = new ArrayList<>(cfg.interceptors());
        if (cfg.rateLimitPerWindow() != null) {
            list.add(new RateLimitInterceptor(
                    cfg.rateLimitPerWindow(), cfg.rateLimitWindowSeconds()));
        }
        if (cfg.redactPii()) {
            list.add(new RedactingInterceptor());
        }
        return list;
    }

    @NonNull
    @SuppressWarnings("deprecation")
    private static String processSuffix(@NonNull LogConfig cfg) {
        if (!cfg.processNameSuffix()) {
            return "";
        }
        return String.format(Locale.US, "%05x", Process.myPid());
    }

}
