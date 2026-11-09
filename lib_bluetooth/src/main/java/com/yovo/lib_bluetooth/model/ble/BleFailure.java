package com.yovo.lib_bluetooth.model.ble;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.model.common.Failure;

import java.util.UUID;

/**
 * BLE 公开失败模型，携带语义错误码、原始底层状态码、追踪 ID、建议动作与目标上下文。
 */
public final class BleFailure extends Failure {

    public static final int NO_RAW_STATUS = -1;

    @NonNull
    private final BleErrorCode errorCode;
    @Nullable
    private final BleDeviceId deviceId;
    @Nullable
    private final UUID serviceUuid;
    @Nullable
    private final UUID characteristicUuid;
    @Nullable
    private final UUID descriptorUuid;
    private final int rawStatusCode;
    @Nullable
    private final String traceId;
    @Nullable
    private final String suggestedAction;

    public BleFailure(@NonNull BleErrorCode errorCode,
                      @NonNull String message,
                      @Nullable Throwable cause,
                      boolean recoverable,
                      boolean retryable,
                      @NonNull String sourceModule,
                      @Nullable BleDeviceId deviceId,
                      @Nullable UUID serviceUuid,
                      @Nullable UUID characteristicUuid) {
        this(errorCode, message, cause, recoverable, retryable, sourceModule,
                deviceId, serviceUuid, characteristicUuid, null,
                NO_RAW_STATUS, null, null);
    }

    public BleFailure(@NonNull BleErrorCode errorCode,
                      @NonNull String message,
                      @Nullable Throwable cause,
                      boolean recoverable,
                      boolean retryable,
                      @NonNull String sourceModule,
                      @Nullable BleDeviceId deviceId,
                      @Nullable UUID serviceUuid,
                      @Nullable UUID characteristicUuid,
                      @Nullable UUID descriptorUuid,
                      int rawStatusCode,
                      @Nullable String traceId,
                      @Nullable String suggestedAction) {
        super(errorCode.name(), message, cause, recoverable, retryable, sourceModule);
        this.errorCode = errorCode;
        this.deviceId = deviceId;
        this.serviceUuid = serviceUuid;
        this.characteristicUuid = characteristicUuid;
        this.descriptorUuid = descriptorUuid;
        this.rawStatusCode = rawStatusCode;
        this.traceId = traceId;
        this.suggestedAction = suggestedAction;
    }

    @NonNull
    public BleErrorCode errorCode() {
        return errorCode;
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

    @Nullable
    public UUID descriptorUuid() {
        return descriptorUuid;
    }

    /** 底层 Android GATT / HCI / Scan 原始状态码；无底层状态码时为 {@link #NO_RAW_STATUS} (-1)。 */
    public int rawStatusCode() {
        return rawStatusCode;
    }

    @Nullable
    public String traceId() {
        return traceId;
    }

    @Nullable
    public String suggestedAction() {
        return suggestedAction;
    }

    @NonNull
    @Override
    public String toString() {
        return "BleFailure{"
                + "errorCode=" + errorCode
                + ", rawStatusCode=" + rawStatusCode
                + ", recoverable=" + recoverable()
                + ", retryable=" + retryable()
                + ", sourceModule='" + sourceModule() + '\''
                + (deviceId != null ? ", deviceId=" + deviceId : "")
                + (traceId != null ? ", traceId='" + traceId + '\'' : "")
                + ", message='" + message() + '\''
                + '}';
    }
}
