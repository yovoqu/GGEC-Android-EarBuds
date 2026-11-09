package com.ggec.earbuds.domain.device.port;

/** 设备模块的本地偏好：最近连接的设备。 */
public interface DevicePreferences {

    String lastMac();

    void rememberDevice(String mac, String modelId);
}
