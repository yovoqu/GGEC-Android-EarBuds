package com.ggec.earbuds.domain.device.usecase;

import com.ggec.earbuds.domain.device.model.DeviceAction;
import com.ggec.earbuds.domain.device.model.DeviceWriteResult;
import com.ggec.earbuds.domain.device.port.DeviceLinkPort;

import java.util.Objects;
import java.util.function.Consumer;

/** 执行一个类型化设备动作（找耳机、重启、录音、双连接等）。 */
public final class RunDeviceAction {

    private final DeviceLinkPort port;

    public RunDeviceAction(DeviceLinkPort port) {
        this.port = Objects.requireNonNull(port, "port");
    }

    public void execute(DeviceAction action, Consumer<DeviceWriteResult> callback) {
        port.perform(Objects.requireNonNull(action, "action"), callback);
    }
}
