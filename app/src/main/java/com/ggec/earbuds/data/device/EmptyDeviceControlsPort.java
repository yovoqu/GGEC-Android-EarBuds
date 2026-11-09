package com.ggec.earbuds.data.device;

import com.ggec.earbuds.domain.device.model.DeviceControls;
import com.ggec.earbuds.domain.device.port.DeviceControlsPort;
import com.ggec.earbuds.domain.device.port.Subscription;

import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * 空设备控件端口：未接入真实协议控制通道时只读返回空控件状态，不伪造默认开关与可写反馈。
 */
public final class EmptyDeviceControlsPort implements DeviceControlsPort {

    private static final Subscription NO_OP_SUBSCRIPTION = () -> {
    };

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public DeviceControls current() {
        return DeviceControls.empty();
    }

    @Override
    public Subscription observe(Consumer<DeviceControls> listener) {
        if (listener != null) {
            listener.accept(DeviceControls.empty());
        }
        return NO_OP_SUBSCRIPTION;
    }

    @Override
    public void update(UnaryOperator<DeviceControls> change) {
        // 未接入真实协议通道前不伪造本地控件变更。
    }
}
