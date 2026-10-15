package com.yovo.lib_common.system.file.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 文件操作失败描述。
 */
public final class FileFailure {

    @NonNull
    private final FileFailureCode code;

    @NonNull
    private final String message;

    @Nullable
    private final Throwable cause;

    @Nullable
    private final AccessCheckResult access;

    public FileFailure(@NonNull FileFailureCode code,
                       @NonNull String message,
                       @Nullable Throwable cause,
                       @Nullable AccessCheckResult access) {
        this.code = code;
        this.message = message;
        this.cause = cause;
        this.access = access;
    }

    @NonNull
    public static FileFailure of(@NonNull FileFailureCode code,
                                 @NonNull String message) {
        return new FileFailure(code, message, null, null);
    }

    @NonNull
    public static FileFailure of(@NonNull FileFailureCode code,
                                 @NonNull String message,
                                 @Nullable Throwable cause) {
        return new FileFailure(code, message, cause, null);
    }

    @NonNull
    public FileFailure withAccess(@Nullable AccessCheckResult access) {
        return new FileFailure(code, message, cause, access);
    }

    @NonNull
    public FileFailureCode code() {
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

    @Nullable
    public AccessCheckResult access() {
        return access;
    }

    @NonNull
    @Override
    public String toString() {
        return "FileFailure{" + code + ": " + message + "}";
    }
}
