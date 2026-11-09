package com.yovo.lib_bluetooth.model.ble;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * BLE 单次扫描结果，包含设备身份、RSSI、完整解析后的 {@link BleScanRecord} 及扩展广播属性。
 */
public final class BleScanResult {

    public static final int SID_NOT_PRESENT = 0xFF;
    public static final int TX_POWER_NOT_PRESENT = 127;
    public static final int PERIODIC_INTERVAL_NOT_PRESENT = 0;

    @NonNull
    private final BleDeviceIdentity identity;
    private final int rssi;
    @NonNull
    private final BleScanRecord scanRecord;
    private final long observedAtMillis;
    private final boolean connectable;
    private final boolean legacy;
    @NonNull
    private final BlePhy primaryPhy;
    @NonNull
    private final BlePhy secondaryPhy;
    private final int advertisingSid;
    private final int txPower;
    private final int periodicAdvertisingIntervalUnits;
    @NonNull
    private final BleScanCallbackType callbackType;

    public BleScanResult(@NonNull BleDeviceIdentity identity, int rssi, long observedAtMillis) {
        this(identity, rssi, BleScanRecord.empty(), observedAtMillis, true, true,
                BlePhy.LE_1M, BlePhy.UNKNOWN, SID_NOT_PRESENT, TX_POWER_NOT_PRESENT,
                PERIODIC_INTERVAL_NOT_PRESENT, BleScanCallbackType.ALL_MATCHES);
    }

    public BleScanResult(@NonNull BleDeviceIdentity identity,
                         int rssi,
                         @NonNull BleScanRecord scanRecord,
                         long observedAtMillis,
                         boolean connectable,
                         boolean legacy,
                         @NonNull BlePhy primaryPhy,
                         @NonNull BlePhy secondaryPhy,
                         int advertisingSid,
                         int txPower,
                         int periodicAdvertisingIntervalUnits,
                         @NonNull BleScanCallbackType callbackType) {
        this.identity = Objects.requireNonNull(identity, "identity");
        this.rssi = rssi;
        this.scanRecord = Objects.requireNonNull(scanRecord, "scanRecord");
        this.observedAtMillis = observedAtMillis;
        this.connectable = connectable;
        this.legacy = legacy;
        this.primaryPhy = Objects.requireNonNull(primaryPhy, "primaryPhy");
        this.secondaryPhy = Objects.requireNonNull(secondaryPhy, "secondaryPhy");
        this.advertisingSid = advertisingSid;
        this.txPower = txPower;
        this.periodicAdvertisingIntervalUnits = periodicAdvertisingIntervalUnits;
        this.callbackType = Objects.requireNonNull(callbackType, "callbackType");
    }

    @NonNull
    public BleDeviceIdentity identity() {
        return identity;
    }

    public int rssi() {
        return rssi;
    }

    @NonNull
    public BleScanRecord scanRecord() {
        return scanRecord;
    }

    public long observedAtMillis() {
        return observedAtMillis;
    }

    public boolean isConnectable() {
        return connectable;
    }

    public boolean isLegacy() {
        return legacy;
    }

    @NonNull
    public BlePhy primaryPhy() {
        return primaryPhy;
    }

    @NonNull
    public BlePhy secondaryPhy() {
        return secondaryPhy;
    }

    public int advertisingSid() {
        return advertisingSid;
    }

    public int txPower() {
        return txPower;
    }

    public int periodicAdvertisingIntervalUnits() {
        return periodicAdvertisingIntervalUnits;
    }

    @NonNull
    public BleScanCallbackType callbackType() {
        return callbackType;
    }

    @NonNull
    public BleScanResult withCallbackType(@NonNull BleScanCallbackType type) {
        return new BleScanResult(identity, rssi, scanRecord, observedAtMillis, connectable,
                legacy, primaryPhy, secondaryPhy, advertisingSid, txPower,
                periodicAdvertisingIntervalUnits, type);
    }
}
