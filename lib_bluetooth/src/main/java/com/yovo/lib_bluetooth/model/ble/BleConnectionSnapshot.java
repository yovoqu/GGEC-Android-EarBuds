package com.yovo.lib_bluetooth.model.ble;

import androidx.annotation.NonNull;

/**
 * BLE 连接状态快照。
 */
public final class BleConnectionSnapshot {

    @NonNull
    private final BleDeviceId deviceId;
    @NonNull
    private final BleConnectionState state;
    private final long updatedAtMillis;

    public BleConnectionSnapshot(@NonNull BleDeviceId deviceId,
                                 @NonNull BleConnectionState state,
                                 long updatedAtMillis) {
        this.deviceId = deviceId;
        this.state = state;
        this.updatedAtMillis = updatedAtMillis;
    }

    @NonNull
    public BleDeviceId deviceId() {
        return deviceId;
    }

    @NonNull
    public BleConnectionState state() {
        return state;
    }

    public long updatedAtMillis() {
        return updatedAtMillis;
    }
}
