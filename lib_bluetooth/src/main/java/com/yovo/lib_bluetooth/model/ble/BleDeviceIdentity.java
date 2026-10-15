package com.yovo.lib_bluetooth.model.ble;

import androidx.annotation.NonNull;

/**
 * BLE 设备身份信息。
 */
public final class BleDeviceIdentity {

    @NonNull
    private final BleDeviceId deviceId;
    @NonNull
    private final String displayName;
    private final long updatedAtMillis;

    public BleDeviceIdentity(@NonNull BleDeviceId deviceId,
                             @NonNull String displayName,
                             long updatedAtMillis) {
        this.deviceId = deviceId;
        this.displayName = displayName;
        this.updatedAtMillis = updatedAtMillis;
    }

    @NonNull
    public BleDeviceId deviceId() {
        return deviceId;
    }

    @NonNull
    public String displayName() {
        return displayName;
    }

    public long updatedAtMillis() {
        return updatedAtMillis;
    }
}
