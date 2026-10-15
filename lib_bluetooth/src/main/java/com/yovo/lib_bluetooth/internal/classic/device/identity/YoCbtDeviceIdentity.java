package com.yovo.lib_bluetooth.internal.classic.device.identity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 设备身份信息，包含设备 ID、MAC 地址、名称及类型等基础字段。
 */

public final class YoCbtDeviceIdentity {
    private final YoCbtDeviceId deviceId;
    private final String address;
    private final YoCbtAddressType addressType;
    private final YoCbtDeviceType deviceType;
    private final YoCbtDeviceAlias alias;

    public YoCbtDeviceIdentity(@NonNull YoCbtDeviceId deviceId,
                               @NonNull String address,
                               @NonNull YoCbtAddressType addressType,
                               @NonNull YoCbtDeviceType deviceType,
                               @Nullable String displayName) {
        this.deviceId = deviceId;
        this.address = address;
        this.addressType = addressType;
        this.deviceType = deviceType;
        this.alias = new YoCbtDeviceAlias(displayName == null ? "" : displayName, "");
    }

    @NonNull
    public YoCbtDeviceId getDeviceId() {
        return deviceId;
    }

    @NonNull
    public String getAddress() {
        return address;
    }

    @NonNull
    public YoCbtAddressType getAddressType() {
        return addressType;
    }

    @NonNull
    public YoCbtDeviceType getDeviceType() {
        return deviceType;
    }

    @NonNull
    public String getDisplayName() {
        return alias.getDisplayName();
    }

    @NonNull
    public YoCbtDeviceAlias getAlias() {
        return alias;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof YoCbtDeviceIdentity)) return false;
        YoCbtDeviceIdentity that = (YoCbtDeviceIdentity) o;
        return deviceId.equals(that.deviceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(deviceId);
    }
}
