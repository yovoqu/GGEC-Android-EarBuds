package com.yovo.lib_bluetooth.internal.common.result;

import com.yovo.lib_bluetooth.internal.common.error.YoErrorCode;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 失败结果基类，携带错误码、消息、可追溯 ID 与可恢复/可重试标记。
 */
public class YoFailure {
    private final YoErrorCode errorCode;
    private final String message;
    private final Throwable cause;
    private final boolean recoverable;
    private final boolean retryable;
    private final String sourceModule;
    private final String traceId;

    public YoFailure(@NonNull YoErrorCode errorCode,
                     @NonNull String message,
                     @Nullable Throwable cause,
                     boolean recoverable,
                     boolean retryable,
                     @NonNull String sourceModule,
                     @NonNull String traceId) {
        this.errorCode = errorCode;
        this.message = message;
        this.cause = cause;
        this.recoverable = recoverable;
        this.retryable = retryable;
        this.sourceModule = sourceModule;
        this.traceId = traceId;
    }

    @NonNull
    public YoErrorCode getErrorCode() {
        return errorCode;
    }

    @NonNull
    public String getMessage() {
        return message;
    }

    @Nullable
    public Throwable getCause() {
        return cause;
    }

    public boolean isRecoverable() {
        return recoverable;
    }

    public boolean isRetryable() {
        return retryable;
    }

    @NonNull
    public String getSourceModule() {
        return sourceModule;
    }

    @NonNull
    public String getTraceId() {
        return traceId;
    }
}
