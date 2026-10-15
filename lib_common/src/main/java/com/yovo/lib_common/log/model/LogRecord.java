package com.yovo.lib_common.log.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.Map;

/**
 * 日志记录：调用线程填充时间戳/线程/上下文快照，异步写线程只读。
 */
public final class LogRecord {

    private final long timestampMillis;

    @NonNull
    private final LogLevel level;

    @NonNull
    private final String tag;

    @NonNull
    private final String message;

    @NonNull
    private final String threadName;

    private final long threadId;

    @Nullable
    private final Throwable throwable;

    @NonNull
    private final Map<String, String> context;

    public LogRecord(long timestampMillis,
                     @NonNull LogLevel level,
                     @NonNull String tag,
                     @NonNull String message,
                     @NonNull String threadName,
                     long threadId,
                     @Nullable Throwable throwable,
                     @NonNull Map<String, String> context) {
        this.timestampMillis = timestampMillis;
        this.level = level;
        this.tag = tag;
        this.message = message;
        this.threadName = threadName;
        this.threadId = threadId;
        this.throwable = throwable;
        this.context = Collections.unmodifiableMap(context);
    }

    public long timestampMillis() {
        return timestampMillis;
    }

    @NonNull
    public LogLevel level() {
        return level;
    }

    @NonNull
    public String tag() {
        return tag;
    }

    @NonNull
    public String message() {
        return message;
    }

    @NonNull
    public String threadName() {
        return threadName;
    }

    public long threadId() {
        return threadId;
    }

    @Nullable
    public Throwable throwable() {
        return throwable;
    }

    @NonNull
    public Map<String, String> context() {
        return context;
    }
}
