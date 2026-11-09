package com.yovo.lib_bluetooth.model.ble;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * BLE 设备身份信息，区分地址类型与双模射频类型。
 */
public final class BleDeviceIdentity {

    @NonNull
    private final BleDeviceId deviceId;
    @NonNull
    private final String displayName;
    @Nullable
    private final String alias;
    @NonNull
    private final BleAddressType addressType;
    @NonNull
    private final BleDeviceType deviceType;
    private final long updatedAtMillis;

    public BleDeviceIdentity(@NonNull BleDeviceId deviceId,
                             @NonNull String displayName,
                             long updatedAtMillis) {
        this(deviceId, displayName, null, BleAddressType.UNKNOWN, BleDeviceType.LE, updatedAtMillis);
    }

    public BleDeviceIdentity(@NonNull BleDeviceId deviceId,
                             @NonNull String displayName,
                             @Nullable String alias,
                             @NonNull BleAddressType addressType,
                             @NonNull BleDeviceType deviceType,
                             long updatedAtMillis) {
        this.deviceId = Objects.requireNonNull(deviceId, "deviceId");
        this.displayName = displayName != null ? displayName : "";
        this.alias = alias;
        this.addressType = Objects.requireNonNull(addressType, "addressType");
        this.deviceType = Objects.requireNonNull(deviceType, "deviceType");
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

    @Nullable
    public String alias() {
        return alias;
    }

    @NonNull
    public BleAddressType addressType() {
        return addressType;
    }

    @NonNull
    public BleDeviceType deviceType() {
        return deviceType;
    }

    public long updatedAtMillis() {
        return updatedAtMillis;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BleDeviceIdentity)) return false;
        BleDeviceIdentity that = (BleDeviceIdentity) o;
        return deviceId.equals(that.deviceId)
                && displayName.equals(that.displayName)
                && Objects.equals(alias, that.alias)
                && addressType == that.addressType
                && deviceType == that.deviceType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(deviceId, displayName, alias, addressType, deviceType);
    }
}
