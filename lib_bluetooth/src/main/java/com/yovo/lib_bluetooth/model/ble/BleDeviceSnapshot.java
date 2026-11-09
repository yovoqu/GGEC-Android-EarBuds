package com.yovo.lib_bluetooth.model.ble;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * 单台 BLE 设备全局聚合只读快照（设备身份 / 配对状态 / 连接状态 / 最近扫描结果 / GATT 数据库）。
 */
public final class BleDeviceSnapshot {

    @NonNull
    private final BleDeviceIdentity identity;
    @NonNull
    private final BleBondState bondState;
    @Nullable
    private final BleConnectionSnapshot connectionSnapshot;
    @Nullable
    private final BleScanResult lastScanResult;
    @Nullable
    private final BleGattDatabaseSnapshot gattDatabase;
    private final long updatedAtMillis;

    public BleDeviceSnapshot(@NonNull BleDeviceIdentity identity,
                             @NonNull BleBondState bondState,
                             @Nullable BleConnectionSnapshot connectionSnapshot,
                             @Nullable BleScanResult lastScanResult,
                             @Nullable BleGattDatabaseSnapshot gattDatabase,
                             long updatedAtMillis) {
        this.identity = Objects.requireNonNull(identity, "identity");
        this.bondState = Objects.requireNonNull(bondState, "bondState");
        this.connectionSnapshot = connectionSnapshot;
        this.lastScanResult = lastScanResult;
        this.gattDatabase = gattDatabase;
        this.updatedAtMillis = updatedAtMillis;
    }

    @NonNull
    public BleDeviceId deviceId() {
        return identity.deviceId();
    }

    @NonNull
    public BleDeviceIdentity identity() {
        return identity;
    }

    @NonNull
    public BleBondState bondState() {
        return bondState;
    }

    @Nullable
    public BleConnectionSnapshot connectionSnapshot() {
        return connectionSnapshot;
    }

    @Nullable
    public BleScanResult lastScanResult() {
        return lastScanResult;
    }

    @Nullable
    public BleGattDatabaseSnapshot gattDatabase() {
        return gattDatabase;
    }

    public long updatedAtMillis() {
        return updatedAtMillis;
    }
}
