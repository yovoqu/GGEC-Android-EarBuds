package com.yovo.lib_bluetooth.model.ble;

import androidx.annotation.NonNull;

/**
 * BLE 单次扫描结果。
 */
public final class BleScanResult {

    @NonNull
    private final BleDeviceIdentity identity;
    private final int rssi;
    private final long observedAtMillis;

    public BleScanResult(@NonNull BleDeviceIdentity identity, int rssi, long observedAtMillis) {
        this.identity = identity;
        this.rssi = rssi;
        this.observedAtMillis = observedAtMillis;
    }

    @NonNull
    public BleDeviceIdentity identity() {
        return identity;
    }

    public int rssi() {
        return rssi;
    }

    public long observedAtMillis() {
        return observedAtMillis;
    }
}
