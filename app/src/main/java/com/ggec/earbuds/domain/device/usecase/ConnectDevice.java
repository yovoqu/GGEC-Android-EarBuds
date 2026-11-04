package com.ggec.earbuds.domain.device.usecase;

import com.ggec.earbuds.domain.device.port.DevicePort;
import com.ggec.earbuds.domain.device.port.DevicePreferences;

/** 连接 / 断开当前设备。 */
public final class ConnectDevice {

    private final DevicePort port;
    private final DevicePreferences preferences;

    public ConnectDevice(DevicePort port, DevicePreferences preferences) {
        this.port = port;
        this.preferences = preferences;
    }

    public void execute(String modelId, String variantId, String mac) {
        preferences.rememberDevice(mac, modelId);
        port.connect(modelId, variantId, mac);
    }

    public void disconnect() {
        port.disconnect();
    }
}
