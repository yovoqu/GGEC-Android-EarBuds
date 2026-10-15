package com.yovo.lib_bluetooth.internal.model.ble;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.model.ble.BleConnectionSnapshot;
import com.yovo.lib_bluetooth.model.ble.BleConnectionState;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;

/**
 * 内部 GATT 会话状态：仅供 internal/ble 使用，不进入公开 API。
 */
public final class BleGattSessionState {

    @NonNull
    private final BleDeviceId deviceId;
    @NonNull
    private volatile BleConnectionState state;
    private volatile long updatedAtMillis;

    public BleGattSessionState(@NonNull BleDeviceId deviceId) {
        this.deviceId = deviceId;
        this.state = BleConnectionState.IDLE;
        this.updatedAtMillis = System.currentTimeMillis();
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

    public void transition(@NonNull BleConnectionState next) {
        this.state = next;
        this.updatedAtMillis = System.currentTimeMillis();
    }

    @NonNull
    public BleConnectionSnapshot snapshot() {
        return new BleConnectionSnapshot(deviceId, state, updatedAtMillis);
    }
}
