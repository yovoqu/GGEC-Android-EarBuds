package com.yovo.lib_bluetooth.model.ble;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.model.common.Failure;

import java.util.UUID;

/**
 * BLE 公开失败模型。
 */
public final class BleFailure extends Failure {

    @Nullable
    private final BleDeviceId deviceId;
    @Nullable
    private final UUID serviceUuid;
    @Nullable
    private final UUID characteristicUuid;

    public BleFailure(@NonNull BleErrorCode errorCode,
                      @NonNull String message,
                      @Nullable Throwable cause,
                      boolean recoverable,
                      boolean retryable,
                      @NonNull String sourceModule,
                      @Nullable BleDeviceId deviceId,
                      @Nullable UUID serviceUuid,
                      @Nullable UUID characteristicUuid) {
        super(errorCode.name(), message, cause, recoverable, retryable, sourceModule);
        this.deviceId = deviceId;
        this.serviceUuid = serviceUuid;
        this.characteristicUuid = characteristicUuid;
    }

    @Nullable
    public BleDeviceId deviceId() {
        return deviceId;
    }

    @Nullable
    public UUID serviceUuid() {
        return serviceUuid;
    }

    @Nullable
    public UUID characteristicUuid() {
        return characteristicUuid;
    }
}
