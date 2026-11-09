package com.yovo.lib_bluetooth.model.ble;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * BLE 连接实时快照，涵盖状态、MTU、有效载荷上限、PHY、连接参数、配对状态、RSSI、重试次数与最近失败原因。
 */
public final class BleConnectionSnapshot {

    public static final int DEFAULT_ATT_MTU = 23;
    public static final int MAX_ATT_WRITE_PAYLOAD = 512;

    @NonNull
    private final BleDeviceId deviceId;
    @NonNull
    private final BleConnectionState state;
    private final int mtu;
    @NonNull
    private final BlePhyConfig phy;
    @Nullable
    private final BleConnectionParameters connectionParameters;
    @NonNull
    private final BleBondState bondState;
    @Nullable
    private final Integer lastRssi;
    private final int retryAttempt;
    private final boolean servicesDiscovered;
    @Nullable
    private final BleFailure lastFailure;
    private final long updatedAtMillis;

    public BleConnectionSnapshot(@NonNull BleDeviceId deviceId,
                                 @NonNull BleConnectionState state,
                                 long updatedAtMillis) {
        this(deviceId, state, DEFAULT_ATT_MTU, BlePhyConfig.le1m(), null,
                BleBondState.UNKNOWN, null, 0, false, null, updatedAtMillis);
    }

    public BleConnectionSnapshot(@NonNull BleDeviceId deviceId,
                                 @NonNull BleConnectionState state,
                                 int mtu,
                                 @NonNull BlePhyConfig phy,
                                 @Nullable BleConnectionParameters connectionParameters,
                                 @NonNull BleBondState bondState,
                                 @Nullable Integer lastRssi,
                                 int retryAttempt,
                                 boolean servicesDiscovered,
                                 @Nullable BleFailure lastFailure,
                                 long updatedAtMillis) {
        this.deviceId = Objects.requireNonNull(deviceId, "deviceId");
        this.state = Objects.requireNonNull(state, "state");
        this.mtu = Math.max(DEFAULT_ATT_MTU, mtu);
        this.phy = Objects.requireNonNull(phy, "phy");
        this.connectionParameters = connectionParameters;
        this.bondState = Objects.requireNonNull(bondState, "bondState");
        this.lastRssi = lastRssi;
        this.retryAttempt = retryAttempt;
        this.servicesDiscovered = servicesDiscovered;
        this.lastFailure = lastFailure;
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

    public boolean isConnected() {
        return state == BleConnectionState.CONNECTED
                || state == BleConnectionState.BONDING_WAIT
                || state == BleConnectionState.DISCOVERING_SERVICES
                || state == BleConnectionState.INITIALIZING
                || state == BleConnectionState.READY;
    }

    public boolean isReady() {
        return state == BleConnectionState.READY;
    }

    public int mtu() {
        return mtu;
    }

    /** 根据当前 MTU 与 Bluetooth Core Spec 512 字节属性写上限计算单包最大有效载荷。 */
    public int maxWritePayloadBytes(@NonNull BleWriteType writeType) {
        int overhead = writeType == BleWriteType.SIGNED ? 15 : 3;
        return Math.max(20, Math.min(mtu - overhead, MAX_ATT_WRITE_PAYLOAD));
    }

    @NonNull
    public BlePhyConfig phy() {
        return phy;
    }

    @Nullable
    public BleConnectionParameters connectionParameters() {
        return connectionParameters;
    }

    @NonNull
    public BleBondState bondState() {
        return bondState;
    }

    @Nullable
    public Integer lastRssi() {
        return lastRssi;
    }

    public int retryAttempt() {
        return retryAttempt;
    }

    public boolean servicesDiscovered() {
        return servicesDiscovered;
    }

    @Nullable
    public BleFailure lastFailure() {
        return lastFailure;
    }

    public long updatedAtMillis() {
        return updatedAtMillis;
    }
}
