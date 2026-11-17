package com.yovo.lib_bluetooth.model.classic;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.model.common.Failure;

/**
 * 经典蓝牙公开失败模型。
 */
public final class CbtFailure extends Failure {

    @NonNull
    private final CbtErrorCode errorCode;
    @Nullable
    private final CbtDeviceId deviceId;

    public CbtFailure(@NonNull CbtErrorCode errorCode,
                      @NonNull String message,
                      @Nullable Throwable cause,
                      boolean recoverable,
                      boolean retryable,
                      @NonNull String sourceModule,
                      @Nullable CbtDeviceId deviceId) {
        super(errorCode.name(), message, cause, recoverable, retryable, sourceModule);
        this.errorCode = errorCode;
        this.deviceId = deviceId;
    }

    @Nullable
    public CbtDeviceId deviceId() {
        return deviceId;
    }

    @NonNull
    public CbtErrorCode errorCode() {
        return errorCode;
    }
}
