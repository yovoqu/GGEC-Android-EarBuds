package com.yovo.lib_bluetooth.model.ble;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * 一次 BLE 观察的身份。只描述 LE 地址、LE 广播本地名和协议栈报告的射频模式。
 *
 * <p>经典蓝牙 BD_ADDR 与经典名称不在这里。{@link #advertisingName()} 只来自广播 AD Local Name，
 * {@link #alias()} 只来自系统别名。二者都不读取 {@code BluetoothDevice.getName()}。</p>
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

    /**
     * LE 广播本地名（AD 0x08/0x09）。没有广播名时为空串。
     */
    @NonNull
    public String advertisingName() {
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
