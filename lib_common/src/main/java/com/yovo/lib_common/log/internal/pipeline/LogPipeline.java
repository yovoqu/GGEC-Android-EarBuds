package com.yovo.lib_common.log.internal.pipeline;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_common.log.internal.async.AsyncLogWriter;
import com.yovo.lib_common.log.internal.sink.FileSink;
import com.yovo.lib_common.log.internal.sink.LogcatSink;
import com.yovo.lib_common.log.model.FileLogReason;
import com.yovo.lib_common.log.model.FileLogStatus;
import com.yovo.lib_common.log.model.LogConfig;
import com.yovo.lib_common.log.model.LogInterceptor;
import com.yovo.lib_common.log.model.LogRecord;

import java.util.ArrayList;
import java.util.List;

/**
 * 日志流水线：Interceptor → Logcat（同步）→ 有界队列（异步 File）。
 */
public final class LogPipeline {

    @NonNull
    private final LogConfig config;

    @Nullable
    private final LogcatSink logcatSink;

    @Nullable
    private final FileSink fileSink;

    @Nullable
    private final FileLogStatus fallbackStatus;

    @Nullable
    private final AsyncLogWriter writer;

    @NonNull
    private final List<LogInterceptor> interceptors;

    public LogPipeline(@NonNull LogConfig config,
                       @Nullable LogcatSink logcatSink,
                       @Nullable FileSink fileSink,
                       @Nullable AsyncLogWriter writer,
                       @Nullable FileLogStatus fallbackStatus,
                       @NonNull List<LogInterceptor> interceptors) {
        this.config = config;
        this.logcatSink = logcatSink;
        this.fileSink = fileSink;
        this.writer = writer;
        this.fallbackStatus = fallbackStatus;
        this.interceptors = new ArrayList<>(interceptors);
    }

    /**
     * @return 是否被拦截器丢弃。
     */
    public boolean log(@NonNull LogRecord record) {
        LogRecord current = record;
        for (LogInterceptor interceptor : interceptors) {
            current = interceptor.intercept(current);
            if (current == null) {
                return false;
            }
        }
        if (logcatSink != null) {
            logcatSink.write(current);
        }
        if (writer != null) {
            writer.enqueue(current);
        }
        return true;
    }

    @NonNull
    public FileLogStatus fileStatus() {
        return fileSink != null
                ? fileSink.status()
                : fallbackStatus != null
                ? fallbackStatus
                : FileLogStatus.disabled(FileLogReason.NOT_REQUESTED, "file sink not installed");
    }

    public void flush(long timeoutMs) {
        if (writer != null) {
            writer.flush(timeoutMs);
        } else if (fileSink != null) {
            fileSink.flush();
        }
    }

    public void shutdown() {
        if (writer != null) {
            writer.shutdown();
        } else if (fileSink != null) {
            fileSink.close();
        }
    }
}
