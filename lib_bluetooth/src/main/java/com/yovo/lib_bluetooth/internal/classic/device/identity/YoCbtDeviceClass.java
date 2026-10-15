package com.yovo.lib_bluetooth.internal.classic.device.identity;

import androidx.annotation.NonNull;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 蓝牙 Class of Device（CoD）信息，含主类与子类。
 */

public final class YoCbtDeviceClass {
    private final int rawClass;
    private final int majorDeviceClass;
    private final int deviceClass;

    public YoCbtDeviceClass(int rawClass, int majorDeviceClass, int deviceClass) {
        this.rawClass = rawClass;
        this.majorDeviceClass = majorDeviceClass;
        this.deviceClass = deviceClass;
    }

    public int getRawClass() {
        return rawClass;
    }

    public int getMajorDeviceClass() {
        return majorDeviceClass;
    }

    public int getDeviceClass() {
        return deviceClass;
    }

    public boolean isKnown() {
        return rawClass != 0 || majorDeviceClass != 0 || deviceClass != 0;
    }

    @NonNull
    public static YoCbtDeviceClass unknown() {
        return new YoCbtDeviceClass(0, 0, 0);
    }
}
