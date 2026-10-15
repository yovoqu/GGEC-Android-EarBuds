package com.yovo.lib_bluetooth.internal.classic.error;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 携带 {@link YoCbtErrorCode} 的非受检 SDK 异常。
 */
public final class YoCbtException extends RuntimeException {

    @NonNull
    private final YoCbtErrorCode errorCode;

    public YoCbtException(@NonNull YoCbtErrorCode errorCode, @NonNull String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public YoCbtException(@NonNull YoCbtErrorCode errorCode,
                          @NonNull String message,
                          @Nullable Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public YoCbtException(@NonNull YoCbtFailure failure) {
        super(failure.getMessage(), failure.getCause());
        this.errorCode = (YoCbtErrorCode) failure.getErrorCode();
    }

    @NonNull
    public YoCbtErrorCode getErrorCode() {
        return errorCode;
    }
}
