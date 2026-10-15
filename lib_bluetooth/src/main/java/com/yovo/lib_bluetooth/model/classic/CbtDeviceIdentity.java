package com.yovo.lib_bluetooth.model.classic;

import androidx.annotation.NonNull;

/**
 * 经典蓝牙设备身份。
 */
public final class CbtDeviceIdentity {

    @NonNull
    private final CbtDeviceId deviceId;
    @NonNull
    private final String address;
    @NonNull
    private final String displayName;

    public CbtDeviceIdentity(@NonNull CbtDeviceId deviceId,
                             @NonNull String address,
                             @NonNull String displayName) {
        this.deviceId = deviceId;
        this.address = address;
        this.displayName = displayName;
    }

    @NonNull
    public CbtDeviceId deviceId() {
        return deviceId;
    }

    @NonNull
    public String address() {
        return address;
    }

    @NonNull
    public String displayName() {
        return displayName;
    }
}
