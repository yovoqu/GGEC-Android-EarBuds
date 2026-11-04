package com.ggec.earbuds.domain.device.port;

/** 设备模块的本地偏好：当前项目名称与最近设备。 */
public interface DevicePreferences {

    String selectedModelId();

    void selectModel(String modelId);

    String lastMac();

    void rememberDevice(String mac, String modelId);
}
