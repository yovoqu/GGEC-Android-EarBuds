package com.yovo.lib_bluetooth.model.classic;

import androidx.annotation.NonNull;

/**
 * 单次设备发现结果。
 */
public final class CbtDiscoveryResult {

    @NonNull
    private final CbtDeviceSnapshot snapshot;
    private final long timestampMillis;

    public CbtDiscoveryResult(@NonNull CbtDeviceSnapshot snapshot, long timestampMillis) {
        this.snapshot = snapshot;
        this.timestampMillis = timestampMillis;
    }

    @NonNull
    public CbtDeviceSnapshot snapshot() {
        return snapshot;
    }

    public long timestampMillis() {
        return timestampMillis;
    }
}
