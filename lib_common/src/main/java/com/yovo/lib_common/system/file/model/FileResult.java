package com.yovo.lib_common.system.file.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 统一文件操作结果：成功携带 value，失败携带 {@link FileFailure}。
 */
public final class FileResult<T> {

    private final boolean success;

    @Nullable
    private final T value;

    @Nullable
    private final FileFailure failure;

    private FileResult(boolean success,
                       @Nullable T value,
                       @Nullable FileFailure failure) {
        this.success = success;
        this.value = value;
        this.failure = failure;
    }

    @NonNull
    public static <T> FileResult<T> ok(@Nullable T value) {
        return new FileResult<>(true, value, null);
    }

    @NonNull
    public static <T> FileResult<T> failure(@NonNull FileFailure failure) {
        return new FileResult<>(false, null, failure);
    }

    @NonNull
    public static <T> FileResult<T> failure(@NonNull FileFailureCode code,
                                            @NonNull String message) {
        return failure(FileFailure.of(code, message));
    }

    @NonNull
    public static <T> FileResult<T> failure(@NonNull FileFailureCode code,
                                            @NonNull String message,
                                            @Nullable Throwable cause) {
        return failure(FileFailure.of(code, message, cause));
    }

    @NonNull
    public static <T> FileResult<T> failure(@NonNull FileFailureCode code,
                                            @NonNull String message,
                                            @Nullable Throwable cause,
                                            @Nullable AccessCheckResult access) {
        return failure(FileFailure.of(code, message, cause).withAccess(access));
    }

    public boolean success() {
        return success;
    }

    @Nullable
    public T value() {
        return value;
    }

    @Nullable
    public FileFailure failure() {
        return failure;
    }

    @NonNull
    public FileFailure requireFailure() {
        if (failure == null) {
            throw new IllegalStateException("Result is success, no failure present");
        }
        return failure;
    }
}
