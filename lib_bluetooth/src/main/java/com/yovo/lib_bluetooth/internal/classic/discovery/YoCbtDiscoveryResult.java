package com.yovo.lib_bluetooth.internal.classic.discovery;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.device.snapshot.YoCbtDeviceSnapshot;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 单次设备发现结果，包含设备快照与发现时间戳。
 */
public final class YoCbtDiscoveryResult {
    private final YoCbtDeviceSnapshot snapshot;
    private final long timestampMillis;

    public YoCbtDiscoveryResult(@NonNull YoCbtDeviceSnapshot snapshot, long timestampMillis) {
        this.snapshot = snapshot;
        this.timestampMillis = timestampMillis;
    }

    @NonNull
    public YoCbtDeviceSnapshot getSnapshot() {
        return snapshot;
    }

    public long getTimestampMillis() {
        return timestampMillis;
    }
}
