package com.yovo.lib_common.log.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_common.system.file.model.PathTarget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * YoLog 初始化配置。
 */
public final class LogConfig {

    @NonNull
    private final LogLevel minLevel;

    private final boolean enableLogcat;

    private final boolean enableFile;

    @Nullable
    private final LogLevel logcatMinLevel;

    @Nullable
    private final LogLevel fileMinLevel;

    @NonNull
    private final String defaultTag;

    private final boolean captureThread;

    private final boolean captureContext;

    @Nullable
    private final PathTarget fileTarget;

    @Nullable
    private final PathTarget fallbackFileTarget;

    @NonNull
    private final FileNamePolicy fileNamePolicy;

    @NonNull
    private final RollPolicy rollPolicy;

    @NonNull
    private final RetentionPolicy retentionPolicy;

    private final int queueCapacity;

    @NonNull
    private final Set<LogLevel> urgentFlushLevels;

    private final long minUsableSpaceBytes;

    private final boolean processNameSuffix;

    private final boolean installCrashFlush;

    private final long crashFlushTimeoutMs;

    private final boolean flushOnBackground;

    @Nullable
    private final Integer rateLimitPerWindow;

    private final int rateLimitWindowSeconds;

    @NonNull
    private final List<LogInterceptor> interceptors;

    private final boolean redactPii;

    private LogConfig(@NonNull Builder builder) {
        minLevel = builder.minLevel;
        enableLogcat = builder.enableLogcat;
        enableFile = builder.enableFile;
        logcatMinLevel = builder.logcatMinLevel;
        fileMinLevel = builder.fileMinLevel;
        defaultTag = builder.defaultTag;
        captureThread = builder.captureThread;
        captureContext = builder.captureContext;
        fileTarget = builder.fileTarget;
        fallbackFileTarget = builder.fallbackFileTarget;
        fileNamePolicy = builder.fileNamePolicy;
        rollPolicy = builder.rollPolicy;
        retentionPolicy = builder.retentionPolicy;
        queueCapacity = builder.queueCapacity;
        urgentFlushLevels = Collections.unmodifiableSet(
                EnumSet.copyOf(builder.urgentFlushLevels));
        minUsableSpaceBytes = builder.minUsableSpaceBytes;
        processNameSuffix = builder.processNameSuffix;
        installCrashFlush = builder.installCrashFlush;
        crashFlushTimeoutMs = builder.crashFlushTimeoutMs;
        flushOnBackground = builder.flushOnBackground;
        rateLimitPerWindow = builder.rateLimitPerWindow;
        rateLimitWindowSeconds = builder.rateLimitWindowSeconds;
        interceptors = Collections.unmodifiableList(
                new ArrayList<>(builder.interceptors));
        redactPii = builder.redactPii;
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    @NonNull
    public LogLevel minLevel() {
        return minLevel;
    }

    public boolean enableLogcat() {
        return enableLogcat;
    }

    public boolean enableFile() {
        return enableFile;
    }

    @Nullable
    public LogLevel logcatMinLevel() {
        return logcatMinLevel;
    }

    @Nullable
    public LogLevel fileMinLevel() {
        return fileMinLevel;
    }

    @NonNull
    public String defaultTag() {
        return defaultTag;
    }

    public boolean captureThread() {
        return captureThread;
    }

    public boolean captureContext() {
        return captureContext;
    }

    @Nullable
    public PathTarget fileTarget() {
        return fileTarget;
    }

    @Nullable
    public PathTarget fallbackFileTarget() {
        return fallbackFileTarget;
    }

    @NonNull
    public FileNamePolicy fileNamePolicy() {
        return fileNamePolicy;
    }

    @NonNull
    public RollPolicy rollPolicy() {
        return rollPolicy;
    }

    @NonNull
    public RetentionPolicy retentionPolicy() {
        return retentionPolicy;
    }

    public int queueCapacity() {
        return queueCapacity;
    }

    @NonNull
    public Set<LogLevel> urgentFlushLevels() {
        return urgentFlushLevels;
    }

    public long minUsableSpaceBytes() {
        return minUsableSpaceBytes;
    }

    public boolean processNameSuffix() {
        return processNameSuffix;
    }

    public boolean installCrashFlush() {
        return installCrashFlush;
    }

    public long crashFlushTimeoutMs() {
        return crashFlushTimeoutMs;
    }

    public boolean flushOnBackground() {
        return flushOnBackground;
    }

    @Nullable
    public Integer rateLimitPerWindow() {
        return rateLimitPerWindow;
    }

    public int rateLimitWindowSeconds() {
        return rateLimitWindowSeconds;
    }

    @NonNull
    public List<LogInterceptor> interceptors() {
        return interceptors;
    }

    public boolean redactPii() {
        return redactPii;
    }

    public static final class Builder {

        private LogLevel minLevel = LogLevel.DEBUG;
        private boolean enableLogcat = true;
        private boolean enableFile = false;
        private LogLevel logcatMinLevel;
        private LogLevel fileMinLevel;
        private String defaultTag = "YoLog";
        private boolean captureThread = true;
        private boolean captureContext = true;
        private PathTarget fileTarget;
        private PathTarget fallbackFileTarget;
        private FileNamePolicy fileNamePolicy = FileNamePolicy.fixed("app.log");
        private RollPolicy rollPolicy = new RollPolicy(2 * 1024 * 1024, 5);
        private RetentionPolicy retentionPolicy =
                new RetentionPolicy(6, 7, 20L * 1024 * 1024);
        private int queueCapacity = 1024;
        private Set<LogLevel> urgentFlushLevels =
                EnumSet.of(LogLevel.ERROR, LogLevel.ASSERT);
        private long minUsableSpaceBytes = 5L * 1024 * 1024;
        private boolean processNameSuffix = true;
        private boolean installCrashFlush = false;
        private long crashFlushTimeoutMs = 2000L;
        private boolean flushOnBackground = true;
        private Integer rateLimitPerWindow;
        private int rateLimitWindowSeconds = 60;
        private List<LogInterceptor> interceptors = new ArrayList<>();
        private boolean redactPii = false;

        @NonNull
        public Builder minLevel(@NonNull LogLevel minLevel) {
            this.minLevel = minLevel;
            return this;
        }

        @NonNull
        public Builder enableLogcat(boolean enableLogcat) {
            this.enableLogcat = enableLogcat;
            return this;
        }

        @NonNull
        public Builder enableFile(boolean enableFile) {
            this.enableFile = enableFile;
            return this;
        }

        @NonNull
        public Builder logcatMinLevel(@Nullable LogLevel logcatMinLevel) {
            this.logcatMinLevel = logcatMinLevel;
            return this;
        }

        @NonNull
        public Builder fileMinLevel(@Nullable LogLevel fileMinLevel) {
            this.fileMinLevel = fileMinLevel;
            return this;
        }

        @NonNull
        public Builder defaultTag(@NonNull String defaultTag) {
            this.defaultTag = defaultTag;
            return this;
        }

        @NonNull
        public Builder captureThread(boolean captureThread) {
            this.captureThread = captureThread;
            return this;
        }

        @NonNull
        public Builder captureContext(boolean captureContext) {
            this.captureContext = captureContext;
            return this;
        }

        @NonNull
        public Builder fileTarget(@Nullable PathTarget fileTarget) {
            this.fileTarget = fileTarget;
            return this;
        }

        @NonNull
        public Builder fallbackFileTarget(@Nullable PathTarget fallbackFileTarget) {
            this.fallbackFileTarget = fallbackFileTarget;
            return this;
        }

        @NonNull
        public Builder fileNamePolicy(@NonNull FileNamePolicy fileNamePolicy) {
            this.fileNamePolicy = fileNamePolicy;
            return this;
        }

        @NonNull
        public Builder rollPolicy(@NonNull RollPolicy rollPolicy) {
            this.rollPolicy = rollPolicy;
            return this;
        }

        @NonNull
        public Builder retentionPolicy(@NonNull RetentionPolicy retentionPolicy) {
            this.retentionPolicy = retentionPolicy;
            return this;
        }

        @NonNull
        public Builder queueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
            return this;
        }

        @NonNull
        public Builder urgentFlushLevels(@NonNull Set<LogLevel> urgentFlushLevels) {
            this.urgentFlushLevels = EnumSet.copyOf(urgentFlushLevels);
            return this;
        }

        @NonNull
        public Builder minUsableSpaceBytes(long minUsableSpaceBytes) {
            this.minUsableSpaceBytes = minUsableSpaceBytes;
            return this;
        }

        @NonNull
        public Builder processNameSuffix(boolean processNameSuffix) {
            this.processNameSuffix = processNameSuffix;
            return this;
        }

        @NonNull
        public Builder installCrashFlush(boolean installCrashFlush) {
            this.installCrashFlush = installCrashFlush;
            return this;
        }

        @NonNull
        public Builder crashFlushTimeoutMs(long crashFlushTimeoutMs) {
            this.crashFlushTimeoutMs = crashFlushTimeoutMs;
            return this;
        }

        @NonNull
        public Builder flushOnBackground(boolean flushOnBackground) {
            this.flushOnBackground = flushOnBackground;
            return this;
        }

        @NonNull
        public Builder rateLimit(@Nullable Integer perWindow, int windowSeconds) {
            this.rateLimitPerWindow = perWindow;
            this.rateLimitWindowSeconds = windowSeconds;
            return this;
        }

        @NonNull
        public Builder addInterceptor(@NonNull LogInterceptor interceptor) {
            interceptors.add(interceptor);
            return this;
        }

        @NonNull
        public Builder redactPii(boolean redactPii) {
            this.redactPii = redactPii;
            return this;
        }

        @NonNull
        public LogConfig build() {
            if (queueCapacity < 1) {
                throw new IllegalArgumentException("queueCapacity must be >= 1");
            }
            if (rateLimitPerWindow != null) {
                if (rateLimitPerWindow <= 0) {
                    throw new IllegalArgumentException(
                            "rateLimitPerWindow must be positive when enabled");
                }
                if (rateLimitWindowSeconds <= 0) {
                    throw new IllegalArgumentException(
                            "rateLimitWindowSeconds must be positive when rate limit enabled");
                }
            }
            if (minUsableSpaceBytes < 0) {
                throw new IllegalArgumentException("minUsableSpaceBytes must be >= 0");
            }
            if (crashFlushTimeoutMs <= 0) {
                throw new IllegalArgumentException("crashFlushTimeoutMs must be positive");
            }
            return new LogConfig(this);
        }
    }
}
