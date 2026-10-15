package com.yovo.lib_bluetooth.internal.classic.profiles.state;

import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtProfileType;
import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 单设备单个 Profile 的连接状态快照。
 */

public final class YoCbtDeviceProfileState {
    private final YoCbtDeviceId deviceId;
    private final YoCbtProfileType profileType;
    private final YoCbtProfileConnectionState connectionState;
    private final YoCbtProfileStateSource source;
    private final long lastChangedAtMillis;

    public YoCbtDeviceProfileState(@NonNull YoCbtDeviceId deviceId,
                                   @NonNull YoCbtProfileType profileType,
                                   @NonNull YoCbtProfileConnectionState connectionState,
                                   @NonNull YoCbtProfileStateSource source,
                                   long lastChangedAtMillis) {
        this.deviceId = deviceId;
        this.profileType = profileType;
        this.connectionState = connectionState;
        this.source = source;
        this.lastChangedAtMillis = lastChangedAtMillis;
    }

    @NonNull
    public YoCbtDeviceId getDeviceId() {
        return deviceId;
    }

    @NonNull
    public YoCbtProfileType getProfileType() {
        return profileType;
    }

    @NonNull
    public YoCbtProfileConnectionState getConnectionState() {
        return connectionState;
    }

    @NonNull
    public YoCbtProfileStateSource getSource() {
        return source;
    }

    public long getLastChangedAtMillis() {
        return lastChangedAtMillis;
    }

    @NonNull
    public YoCbtProfileRuntimeState getRuntimeState() {
        return YoCbtProfileRuntimeStateMapper.fromConnectionState(connectionState);
    }
}
