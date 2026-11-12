package com.ggec.earbuds.domain.device.usecase;

import com.ggec.earbuds.domain.device.model.DeviceAction;
import com.ggec.earbuds.domain.device.model.DeviceWriteResult;
import com.ggec.earbuds.domain.device.port.DevicePort;

import java.util.Objects;
import java.util.function.Consumer;

/** 执行一个类型化设备动作（找耳机、重命名、录音、双连接设备操作等）。 */
public final class RunDeviceAction {

    private final DevicePort port;

    public RunDeviceAction(DevicePort port) {
        this.port = port;
    }

    public void execute(DeviceAction action, Consumer<DeviceWriteResult> callback) {
        port.perform(Objects.requireNonNull(action, "action"),
                Objects.requireNonNull(callback, "callback"));
    }
}
