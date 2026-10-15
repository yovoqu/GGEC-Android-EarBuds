package com.yovo.lib_bluetooth.internal.classic.profiles.a2dp;

import com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtProfileConnectionState;
import com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtProfileStateSource;
import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 单设备 A2DP 完整状态快照（连接、运行时状态、活跃设备等）。
 */

public final class YoCbtA2dpDeviceState {
    private final YoCbtDeviceId deviceId;
    private final YoCbtProfileConnectionState profileState;
    private final YoCbtA2dpRuntimeState runtimeState;
    private final long lastChangedAtMillis;
    private final YoCbtProfileStateSource source;

    public YoCbtA2dpDeviceState(@NonNull YoCbtDeviceId deviceId,
                                @NonNull YoCbtProfileConnectionState profileState,
                                @NonNull YoCbtA2dpRuntimeState runtimeState,
                                long lastChangedAtMillis,
                                @NonNull YoCbtProfileStateSource source) {
        this.deviceId = deviceId;
        this.profileState = profileState;
        this.runtimeState = runtimeState;
        this.lastChangedAtMillis = lastChangedAtMillis;
        this.source = source;
    }

    @NonNull
    public YoCbtDeviceId getDeviceId() {
        return deviceId;
    }

    @NonNull
    public YoCbtProfileConnectionState getProfileState() {
        return profileState;
    }

    @NonNull
    public YoCbtA2dpRuntimeState getRuntimeState() {
        return runtimeState;
    }

    public long getLastChangedAtMillis() {
        return lastChangedAtMillis;
    }

    @NonNull
    public YoCbtProfileStateSource getSource() {
        return source;
    }
}
