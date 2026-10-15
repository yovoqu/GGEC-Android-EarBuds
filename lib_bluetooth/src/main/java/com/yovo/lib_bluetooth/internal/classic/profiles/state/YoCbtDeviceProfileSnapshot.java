package com.yovo.lib_bluetooth.internal.classic.profiles.state;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.device.snapshot.YoCbtDeviceSnapshot;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 设备 Profile 状态快照，包含支持/已连接 Profile 及 A2DP、Headset 等专项状态。
 */

public final class YoCbtDeviceProfileSnapshot {
    private final YoCbtDeviceSnapshot deviceSnapshot;

    public YoCbtDeviceProfileSnapshot(@NonNull YoCbtDeviceSnapshot deviceSnapshot) {
        this.deviceSnapshot = deviceSnapshot;
    }

    @NonNull
    public YoCbtDeviceSnapshot getDeviceSnapshot() {
        return deviceSnapshot;
    }
}
