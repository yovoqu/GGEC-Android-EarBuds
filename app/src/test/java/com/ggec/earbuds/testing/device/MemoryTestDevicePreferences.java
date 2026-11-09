package com.ggec.earbuds.testing.device;

import com.ggec.earbuds.domain.device.port.DevicePreferences;

/** 单测用内存设备偏好。 */
public final class MemoryTestDevicePreferences implements DevicePreferences {

    private String mac;

    @Override
    public String lastMac() {
        return mac;
    }

    @Override
    public void rememberDevice(String mac, String modelId) {
        this.mac = mac;
    }
}
