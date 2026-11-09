package com.ggec.earbuds.domain.device.usecase;

import com.ggec.earbuds.domain.device.model.DeviceControls;
import com.ggec.earbuds.domain.device.port.DeviceControlsPort;
import com.ggec.earbuds.domain.device.port.Subscription;

import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/** 读取并提交设备控件。页面不直接碰端口实现。 */
public final class EditDeviceControls {

    private final DeviceControlsPort port;

    public EditDeviceControls(DeviceControlsPort port) {
        this.port = port;
    }

    public boolean isAvailable() {
        return port.isAvailable();
    }

    public DeviceControls current() {
        return port.current();
    }

    public Subscription observe(Consumer<DeviceControls> listener) {
        return port.observe(listener);
    }

    public void update(UnaryOperator<DeviceControls> change) {
        if (!port.isAvailable()) {
            return;
        }
        port.update(change);
    }
}
