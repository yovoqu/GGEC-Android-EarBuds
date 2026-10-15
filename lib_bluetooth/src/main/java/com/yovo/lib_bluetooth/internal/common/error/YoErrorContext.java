package com.yovo.lib_bluetooth.internal.common.error;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 错误上下文，聚合错误码、消息、traceId 与可恢复/可重试标记。
 */
public class YoErrorContext {
    private final YoErrorCode errorCode;
    private final String message;
    private final Throwable cause;
    private final boolean recoverable;
    private final boolean retryable;
    private final String sourceModule;
    private final String traceId;
    private final long timestamp;

    protected YoErrorContext(Builder builder) {
        this.errorCode = builder.errorCode;
        this.message = builder.message;
        this.cause = builder.cause;
        this.recoverable = builder.recoverable;
        this.retryable = builder.retryable;
        this.sourceModule = builder.sourceModule;
        this.traceId = builder.traceId;
        this.timestamp = builder.timestamp;
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    // ── 访问器 ────────────────────────────────────────────────────

    @NonNull
    public YoErrorCode errorCode() {
        return errorCode;
    }

    @NonNull
    public String message() {
        return message;
    }

    @Nullable
    public Throwable cause() {
        return cause;
    }

    public boolean recoverable() {
        return recoverable;
    }

    public boolean retryable() {
        return retryable;
    }

    @NonNull
    public String sourceModule() {
        return sourceModule;
    }

    @NonNull
    public String traceId() {
        return traceId;
    }

    public long timestamp() {
        return timestamp;
    }

    // ── 构建器 ────────────────────────────────────────────────────

    public static class Builder {
        private YoErrorCode errorCode;
        private String message;
        private Throwable cause;
        private boolean recoverable;
        private boolean retryable;
        private String sourceModule;
        private String traceId;
        private long timestamp;

        protected Builder() {
            this.timestamp = System.currentTimeMillis();
        }

        @NonNull
        public Builder errorCode(@NonNull YoErrorCode errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        @NonNull
        public Builder message(@NonNull String message) {
            this.message = message;
            return this;
        }

        @NonNull
        public Builder cause(@Nullable Throwable cause) {
            this.cause = cause;
            return this;
        }

        @NonNull
        public Builder recoverable(boolean recoverable) {
            this.recoverable = recoverable;
            return this;
        }

        @NonNull
        public Builder retryable(boolean retryable) {
            this.retryable = retryable;
            return this;
        }

        @NonNull
        public Builder sourceModule(@NonNull String sourceModule) {
            this.sourceModule = sourceModule;
            return this;
        }

        @NonNull
        public Builder traceId(@NonNull String traceId) {
            this.traceId = traceId;
            return this;
        }

        @NonNull
        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        @NonNull
        public YoErrorContext build() {
            Objects.requireNonNull(errorCode, "errorCode must not be null");
            Objects.requireNonNull(message, "message must not be null");
            Objects.requireNonNull(sourceModule, "sourceModule must not be null");
            Objects.requireNonNull(traceId, "traceId must not be null");
            return new YoErrorContext(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof YoErrorContext)) return false;
        YoErrorContext that = (YoErrorContext) o;
        return recoverable == that.recoverable
                && retryable == that.retryable
                && timestamp == that.timestamp
                && Objects.equals(errorCode, that.errorCode)
                && Objects.equals(message, that.message)
                && Objects.equals(cause, that.cause)
                && Objects.equals(sourceModule, that.sourceModule)
                && Objects.equals(traceId, that.traceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(errorCode, message, cause, recoverable, retryable, sourceModule, traceId, timestamp);
    }

    @NonNull
    @Override
    public String toString() {
        return "YoErrorContext{"
                + "errorCode=" + errorCode.code()
                + ", message='" + message + '\''
                + ", recoverable=" + recoverable
                + ", retryable=" + retryable
                + ", sourceModule='" + sourceModule + '\''
                + ", traceId='" + traceId + '\''
                + ", timestamp=" + timestamp
                + '}';
    }
}
