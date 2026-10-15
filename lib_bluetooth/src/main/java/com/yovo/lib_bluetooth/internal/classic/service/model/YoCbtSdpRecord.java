package com.yovo.lib_bluetooth.internal.classic.service.model;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;

import java.util.Objects;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 存储在缓存和快照中的设备级 SDP 记录聚合。
 */
public final class YoCbtSdpRecord {

    @NonNull
    private final YoCbtDeviceId deviceId;
    @NonNull
    private final YoCbtSdpResult result;

    public YoCbtSdpRecord(@NonNull YoCbtDeviceId deviceId, @NonNull YoCbtSdpResult result) {
        this.deviceId = Objects.requireNonNull(deviceId, "deviceId must not be null");
        this.result = Objects.requireNonNull(result, "result must not be null");
        if (!deviceId.equals(result.getDeviceId())) {
            throw new IllegalArgumentException("deviceId mismatch between record and result");
        }
    }

    @NonNull
    public YoCbtDeviceId getDeviceId() {
        return deviceId;
    }

    @NonNull
    public YoCbtSdpResult getResult() {
        return result;
    }

    public boolean isEmpty() {
        return result.isEmpty();
    }

    @NonNull
    public static YoCbtSdpRecord of(@NonNull YoCbtSdpResult result) {
        return new YoCbtSdpRecord(result.getDeviceId(), result);
    }
}
