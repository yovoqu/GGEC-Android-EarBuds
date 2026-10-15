package com.yovo.lib_bluetooth.model.classic;

import androidx.annotation.NonNull;

/**
 * 单设备单 Profile 状态。
 */
public final class CbtDeviceProfileState {

    @NonNull
    private final CbtDeviceId deviceId;
    @NonNull
    private final CbtProfileType profileType;
    @NonNull
    private final CbtProfileConnectionState state;
    private final long updatedAtMillis;

    public CbtDeviceProfileState(@NonNull CbtDeviceId deviceId,
                                 @NonNull CbtProfileType profileType,
                                 @NonNull CbtProfileConnectionState state,
                                 long updatedAtMillis) {
        this.deviceId = deviceId;
        this.profileType = profileType;
        this.state = state;
        this.updatedAtMillis = updatedAtMillis;
    }

    @NonNull
    public CbtDeviceId deviceId() {
        return deviceId;
    }

    @NonNull
    public CbtProfileType profileType() {
        return profileType;
    }

    @NonNull
    public CbtProfileConnectionState state() {
        return state;
    }

    public long updatedAtMillis() {
        return updatedAtMillis;
    }
}
