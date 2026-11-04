package com.ggec.earbuds.domain.device.usecase;

import com.ggec.earbuds.domain.device.model.DeviceSnapshot;
import com.ggec.earbuds.domain.device.port.DevicePort;
import com.ggec.earbuds.domain.device.port.Subscription;

import java.util.function.Consumer;

/** 读取并订阅设备标准状态。 */
public final class ObserveDevice {

    private final DevicePort port;

    public ObserveDevice(DevicePort port) {
        this.port = port;
    }

    public DeviceSnapshot current() {
        return port.snapshot();
    }

    public Subscription execute(Consumer<DeviceSnapshot> listener) {
        return port.observe(listener);
    }
}
