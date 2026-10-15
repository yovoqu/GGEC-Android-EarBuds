package com.yovo.lib_bluetooth.model.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 公开错误模型：跨 API 返回的结构化失败信息。
 */
public abstract class Failure {

    @NonNull
    private final String code;
    @NonNull
    private final String message;
    @Nullable
    private final Throwable cause;
    private final boolean recoverable;
    private final boolean retryable;
    @NonNull
    private final String sourceModule;

    protected Failure(@NonNull String code,
                      @NonNull String message,
                      @Nullable Throwable cause,
                      boolean recoverable,
                      boolean retryable,
                      @NonNull String sourceModule) {
        this.code = code;
        this.message = message;
        this.cause = cause;
        this.recoverable = recoverable;
        this.retryable = retryable;
        this.sourceModule = sourceModule;
    }

    @NonNull
    public String code() {
        return code;
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
}
