package com.yovo.lib_bluetooth.internal.classic.discovery;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceClass;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceIdentity;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 发现过程中按设备 ID 聚合的临时结果条目（内部使用）。
 */
final class YoCbtDiscoveryResultEntry {

    @NonNull YoCbtDeviceIdentity identity;
    @NonNull final YoCbtDeviceClass deviceClass;
    int rssi;
    long lastSeenMillis;

    YoCbtDiscoveryResultEntry(@NonNull YoCbtDeviceIdentity identity,
                              @NonNull YoCbtDeviceClass deviceClass,
                              int rssi,
                              long lastSeenMillis) {
        this.identity = identity;
        this.deviceClass = deviceClass;
        this.rssi = rssi;
        this.lastSeenMillis = lastSeenMillis;
    }
}
