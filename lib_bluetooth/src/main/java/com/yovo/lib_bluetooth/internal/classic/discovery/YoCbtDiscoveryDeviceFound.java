package com.yovo.lib_bluetooth.internal.classic.discovery;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceClass;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceIdentity;

import java.util.Objects;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 经典蓝牙扫描发现设备时发出的领域信号。
 */
public final class YoCbtDiscoveryDeviceFound {

    private final YoCbtDeviceIdentity identity;
    private final YoCbtDeviceClass deviceClass;
    private final int rssi;

    public YoCbtDiscoveryDeviceFound(@NonNull YoCbtDeviceIdentity identity,
                                     @NonNull YoCbtDeviceClass deviceClass,
                                     int rssi) {
        this.identity = Objects.requireNonNull(identity, "identity");
        this.deviceClass = Objects.requireNonNull(deviceClass, "deviceClass");
        this.rssi = rssi;
    }

    @NonNull
    public YoCbtDeviceIdentity getIdentity() {
        return identity;
    }

    @NonNull
    public YoCbtDeviceClass getDeviceClass() {
        return deviceClass;
    }

    public int getRssi() {
        return rssi;
    }
}
