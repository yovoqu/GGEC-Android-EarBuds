package com.yovo.lib_bluetooth.model.classic;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * 单设备全部 Profile 状态快照。
 */
public final class CbtDeviceProfileSnapshot {

    @NonNull
    private final CbtDeviceId deviceId;
    @NonNull
    private final CbtDeviceSnapshot deviceSnapshot;
    @NonNull
    private final Map<CbtProfileType, CbtDeviceProfileState> profileStates;

    public CbtDeviceProfileSnapshot(@NonNull CbtDeviceSnapshot deviceSnapshot,
                                    @NonNull Map<CbtProfileType, CbtDeviceProfileState> profileStates) {
        this.deviceId = deviceSnapshot.deviceId();
        this.deviceSnapshot = deviceSnapshot;
        this.profileStates = Collections.unmodifiableMap(new EnumMap<>(profileStates));
    }

    @NonNull
    public CbtDeviceId deviceId() {
        return deviceId;
    }

    @NonNull
    public CbtDeviceSnapshot deviceSnapshot() {
        return deviceSnapshot;
    }

    @NonNull
    public Map<CbtProfileType, CbtDeviceProfileState> profileStates() {
        return profileStates;
    }
}
