package com.ggec.earbuds.domain.device.usecase;

import com.ggec.earbuds.domain.device.model.ControlValue;
import com.ggec.earbuds.domain.device.model.DeviceWriteResult;
import com.ggec.earbuds.domain.device.port.DeviceLinkPort;

import java.util.Objects;
import java.util.function.Consumer;

/** 写入一个能力的控制值。能力是否支持由 biz 判定，本用例只做参数契约检查。 */
public final class WriteDeviceControl {

    private final DeviceLinkPort port;

    public WriteDeviceControl(DeviceLinkPort port) {
        this.port = Objects.requireNonNull(port, "port");
    }

    public void execute(String capabilityId, ControlValue value,
                        Consumer<DeviceWriteResult> callback) {
        if (capabilityId == null || capabilityId.isEmpty()) {
            throw new IllegalArgumentException("capabilityId required");
        }
        Objects.requireNonNull(value, "value");
        port.write(capabilityId, value, callback);
    }
}
