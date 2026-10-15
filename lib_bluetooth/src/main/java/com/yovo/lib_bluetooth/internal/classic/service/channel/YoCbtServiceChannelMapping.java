package com.yovo.lib_bluetooth.internal.classic.service.channel;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;

import java.util.Objects;
import java.util.UUID;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 特定设备的 UUID 到 RFCOMM 信道映射。
 */
public final class YoCbtServiceChannelMapping {

    @NonNull
    private final YoCbtDeviceId deviceId;
    @NonNull
    private final UUID serviceUuid;
    private final int channel;
    @NonNull
    private final YoCbtServiceChannelSource source;

    public YoCbtServiceChannelMapping(@NonNull YoCbtDeviceId deviceId,
                                      @NonNull UUID serviceUuid,
                                      int channel,
                                      @NonNull YoCbtServiceChannelSource source) {
        if (channel < 1 || channel > 30) {
            throw new IllegalArgumentException("RFCOMM channel out of range [1,30]: " + channel);
        }
        this.deviceId = Objects.requireNonNull(deviceId, "deviceId must not be null");
        this.serviceUuid = Objects.requireNonNull(serviceUuid, "serviceUuid must not be null");
        this.channel = channel;
        this.source = Objects.requireNonNull(source, "source must not be null");
    }

    @NonNull
    public YoCbtDeviceId getDeviceId() {
        return deviceId;
    }

    @NonNull
    public UUID getServiceUuid() {
        return serviceUuid;
    }

    public int getChannel() {
        return channel;
    }

    @NonNull
    public YoCbtServiceChannelSource getSource() {
        return source;
    }
}
