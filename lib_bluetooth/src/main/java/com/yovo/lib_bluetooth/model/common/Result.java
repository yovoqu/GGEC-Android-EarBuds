package com.yovo.lib_bluetooth.model.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 公开结果模型：成功携带值，失败携带 {@link Failure}。
 */
public final class Result<T> {

    @Nullable
    private final T value;
    @Nullable
    private final Failure failure;

    private Result(@Nullable T value, @Nullable Failure failure) {
        this.value = value;
        this.failure = failure;
    }

    @NonNull
    public static <T> Result<T> success(@Nullable T value) {
        return new Result<>(value, null);
    }

    @NonNull
    public static <T> Result<T> failure(@NonNull Failure failure) {
        return new Result<>(null, failure);
    }

    public boolean isSuccess() {
        return failure == null;
    }

    public boolean isFailure() {
        return failure != null;
    }

    @Nullable
    public T getValue() {
        return value;
    }

    @NonNull
    public T requireValue() {
        if (value == null) {
            throw new IllegalStateException("Result contains no value: " + failure);
        }
        return value;
    }

    @Nullable
    public Failure getFailure() {
        return failure;
    }
}
