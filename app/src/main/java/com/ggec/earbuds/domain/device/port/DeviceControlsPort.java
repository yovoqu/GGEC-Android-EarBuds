package com.ggec.earbuds.domain.device.port;

import com.ggec.earbuds.domain.device.model.DeviceControls;

import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/** 设备控件的唯一写入口。 */
public interface DeviceControlsPort {

    boolean isAvailable();

    DeviceControls current();

    Subscription observe(Consumer<DeviceControls> listener);

    void update(UnaryOperator<DeviceControls> change);
}
