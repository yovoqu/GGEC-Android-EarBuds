package com.yovo.lib_bluetooth.model.classic;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.model.common.Failure;

/**
 * 经典蓝牙公开失败模型。
 */
public final class CbtFailure extends Failure {

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
        this.deviceId = deviceId;
    }

    @Nullable
    public CbtDeviceId deviceId() {
        return deviceId;
    }

    @NonNull
    public CbtErrorCode errorCode() {
        try {
            return CbtErrorCode.valueOf(code());
        } catch (IllegalArgumentException ignored) {
            return CbtErrorCode.SPP_CONNECT_FAILED;
        }
    }
}
