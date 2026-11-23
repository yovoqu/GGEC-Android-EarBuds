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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * YoLog 核心：init/shutdown 互斥、记录构建、目标选择、降级与未初始化行为。
 *
 * <p>日志管线、配置、崩溃 flush 安装状态与上下文存储都只是本实例的字段；
 * 门面 {@link YoLog} 持有唯一实例，不再以静态可写字段承载装配状态。
 * 需要隔离生命周期（如单测）的调用方可自行 {@code new LogCore()}。</p>
 */
public final class LogCore {

    private final Object lock = new Object();

    private final LogcatSink uninitializedSink = new LogcatSink(LogLevel.DEBUG);

    private final LogContext logContext = new LogContext();

    private final CrashFlushInstaller crashFlushInstaller = new CrashFlushInstaller();

    private final LogLifecycleBinder lifecycleBinder = new LogLifecycleBinder();

    @Nullable
    private LogPipeline pipeline;

    @Nullable
    private LogConfig config;

    @Nullable
    private InitResult lastResult;

    private boolean crashFlushInstalled;

    private boolean shutDown;

    public LogCore() {
    }

    @NonNull
    public InitResult init(@NonNull Context context, @NonNull LogConfig newConfig) {
        synchronized (lock) {
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
                lifecycleBinder.install(this::flush);
            }
            if (newConfig.installCrashFlush()) {
                if (!crashFlushInstalled) {
                    crashFlushInstaller.install((thread, cause) -> {
                        log(LogLevel.ASSERT, "YoLog", "process crash: " + cause, cause);
                        flushCrash();
                    });
                    crashFlushInstalled = true;
                }
            } else {
                crashFlushInstaller.uninstall();
                crashFlushInstalled = false;
            }

            FileLogStatus status = fileSink != null ? fileSink.status() : null;
            boolean fileEnabled = status != null && status.enabled();
            String disabledReason = fileEnabled
                    ? null
                    : (status != null ? status.detail() : selection.detail());
            i("YoLog", "YoLog 已初始化：logcat=" + newConfig.enableLogcat()
                    + "，文件日志=" + fileEnabled
                    + (fileEnabled ? "，目录=" + selection.target()
                    : "，原因=" + disabledReason));
            InitResult result = new InitResult(
                    newConfig.enableLogcat(),
                    fileEnabled,
                    fileEnabled ? selection.target() : null,
                    disabledReason);
            lastResult = result;
            return result;
        }
    }

    /**
     * 只初始化一次：本实例已初始化则直接返回 {@code false}，不重建管线。
     *
     * <p>判定与初始化在同一把实例锁内完成，避免并发入口出现「先查后建」的重复 init。</p>
     */
    public boolean initOnce(@NonNull Context context, @NonNull LogConfig newConfig) {
        synchronized (lock) {
            if (pipeline != null) {
                return false;
            }
            init(context, newConfig);
            return true;
        }
    }

    public boolean isInitialized() {
        return pipeline != null;
    }

    @Nullable
    public InitResult lastResult() {
        return lastResult;
    }

    // ---- LogContext（类 MDC）----

    public void putContext(@NonNull String key, @NonNull String value) {
        logContext.put(key, value);
    }

    public void removeContext(@NonNull String key) {
        logContext.remove(key);
    }

    public void clearContext() {
        logContext.clear();
    }

    public boolean isLoggable(@NonNull LogLevel level) {
        LogConfig current = config;
        LogLevel min = current != null ? current.minLevel() : LogLevel.DEBUG;
        return level.isEnabled(min);
    }

    public void v(@NonNull String message) {
        log(LogLevel.VERBOSE, tag(), message, null);
    }

    public void v(@NonNull String tag, @NonNull String message) {
        log(LogLevel.VERBOSE, tag, message, null);
    }

    public void d(@NonNull String message) {
        log(LogLevel.DEBUG, tag(), message, null);
    }

    public void d(@NonNull String tag, @NonNull String message) {
        log(LogLevel.DEBUG, tag, message, null);
    }

    public void i(@NonNull String message) {
        log(LogLevel.INFO, tag(), message, null);
    }

    public void i(@NonNull String tag, @NonNull String message) {
        log(LogLevel.INFO, tag, message, null);
    }

    public void w(@NonNull String message) {
        log(LogLevel.WARN, tag(), message, null);
    }

    public void w(@NonNull String tag, @NonNull String message) {
        log(LogLevel.WARN, tag, message, null);
    }

    public void w(@NonNull String tag, @NonNull String message,
                  @Nullable Throwable throwable) {
        log(LogLevel.WARN, tag, message, throwable);
    }

    public void v(@NonNull String tag, @NonNull String message,
                  @Nullable Throwable throwable) {
        log(LogLevel.VERBOSE, tag, message, throwable);
    }

    public void d(@NonNull String tag, @NonNull String message,
                  @Nullable Throwable throwable) {
        log(LogLevel.DEBUG, tag, message, throwable);
    }

    public void i(@NonNull String tag, @NonNull String message,
                  @Nullable Throwable throwable) {
        log(LogLevel.INFO, tag, message, throwable);
    }

    public void w(@NonNull String tag, @NonNull String format,
                  @NonNull Object... args) {
        if (!isLoggable(LogLevel.WARN)) {
            return;
        }
        log(LogLevel.WARN, tag, String.format(Locale.US, format, args), null);
    }

    public void e(@NonNull String message) {
        log(LogLevel.ERROR, tag(), message, null);
    }

    public void e(@NonNull String tag, @NonNull String message) {
        log(LogLevel.ERROR, tag, message, null);
    }

    public void e(@NonNull String tag, @NonNull String message,
                  @Nullable Throwable throwable) {
        log(LogLevel.ERROR, tag, message, throwable);
    }

    public void e(@NonNull String tag, @NonNull String format,
                  @NonNull Object... args) {
        if (!isLoggable(LogLevel.ERROR)) {
            return;
        }
        log(LogLevel.ERROR, tag, String.format(Locale.US, format, args), null);
    }

    public void v(@NonNull String tag, @NonNull String format,
                  @NonNull Object... args) {
        if (!isLoggable(LogLevel.VERBOSE)) {
            return;
        }
        log(LogLevel.VERBOSE, tag, String.format(Locale.US, format, args), null);
    }

    public void d(@NonNull String tag, @NonNull String format,
                  @NonNull Object... args) {
        if (!isLoggable(LogLevel.DEBUG)) {
            return;
        }
        log(LogLevel.DEBUG, tag, String.format(Locale.US, format, args), null);
    }

    public void i(@NonNull String tag, @NonNull String format,
                  @NonNull Object... args) {
        if (!isLoggable(LogLevel.INFO)) {
            return;
        }
        log(LogLevel.INFO, tag, String.format(Locale.US, format, args), null);
    }

    public void d(@NonNull String tag, @NonNull Supplier<String> message) {
        lazy(LogLevel.DEBUG, tag, message, null);
    }

    public void e(@NonNull String tag,
                  @NonNull Supplier<String> message,
                  @Nullable Throwable throwable) {
        lazy(LogLevel.ERROR, tag, message, throwable);
    }

    public void log(@NonNull LogLevel level,
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
                captureContext ? logContext.snapshot() : Collections.emptyMap());
        dispatch(record);
    }

    private void lazy(@NonNull LogLevel level,
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
    public FileLogStatus fileStatus() {
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

    public void flush() {
        LogPipeline current = pipeline;
        if (current != null) {
            current.flush(2000L);
        }
    }

    public void flush(long timeoutMs) {
        LogPipeline current = pipeline;
        if (current != null) {
            current.flush(timeoutMs);
        }
    }

    private void flushCrash() {
        LogConfig current = config;
        flush(current != null ? current.crashFlushTimeoutMs() : 2000L);
    }

    public void shutdown() {
        synchronized (lock) {
            shutdownLocked();
        }
    }

    @NonNull
    private String tag() {
        LogConfig current = config;
        return current != null ? current.defaultTag() : "YoLog";
    }

    private void dispatch(@NonNull LogRecord record) {
        LogPipeline current = pipeline;
        if (current != null) {
            current.log(record);
        } else {
            uninitializedSink.write(record);
        }
    }

    private void shutdownLocked() {
        LogPipeline current = pipeline;
        pipeline = null;
        config = null;
        crashFlushInstaller.uninstall();
        crashFlushInstalled = false;
        lifecycleBinder.uninstall();
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
    private static String processSuffix(@NonNull LogConfig cfg) {
        if (!cfg.processNameSuffix()) {
            return "";
        }
        return String.format(Locale.US, "%05x", Process.myPid());
    }

}
