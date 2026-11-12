package com.ggec.earbuds.domain.device.usecase;

import com.ggec.earbuds.domain.device.port.DevicePort;

/** 连接 / 断开当前设备。 */
public final class ConnectDevice {

    private final DevicePort port;

    public ConnectDevice(DevicePort port) {
        this.port = port;
    }

    public void execute(String modelId, String variantId, String bleMac, String bleName) {
        port.connect(modelId, variantId, bleMac, bleName);
    }

    public void disconnect() {
        port.disconnect();
    }
}
