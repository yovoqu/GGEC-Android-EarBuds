package com.yovo.lib_bluetooth.model.classic;

import androidx.annotation.NonNull;

/**
 * 经典蓝牙身份。{@link #classicAddress()} 是 BD_ADDR，{@link #classicName()} 是经典名称。
 * 不保存 BLE 扫描地址，也不保存 BLE 广播本地名。
 */
public final class CbtDeviceIdentity {

    @NonNull
    private final CbtDeviceId deviceId;
    @NonNull
    private final String classicAddress;
    @NonNull
    private final String classicName;

    public CbtDeviceIdentity(@NonNull CbtDeviceId deviceId,
                             @NonNull String classicAddress,
                             @NonNull String classicName) {
        this.deviceId = deviceId;
        this.classicAddress = classicAddress;
        this.classicName = classicName;
    }

    @NonNull
    public CbtDeviceId deviceId() {
        return deviceId;
    }

    /** 经典蓝牙 BD_ADDR。 */
    @NonNull
    public String classicAddress() {
        return classicAddress;
    }

    /** 经典蓝牙名称。 */
    @NonNull
    public String classicName() {
        return classicName;
    }
}
