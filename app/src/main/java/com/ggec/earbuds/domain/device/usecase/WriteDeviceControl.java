package com.ggec.earbuds.domain.device.usecase;

import com.ggec.earbuds.domain.device.model.ControlValue;
import com.ggec.earbuds.domain.device.model.DeviceWriteResult;
import com.ggec.earbuds.domain.device.port.DevicePort;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * 写入一个能力的控制值。页面不提协议、不猜控件类型：控件类型来自能力描述符，
 * 值来自 {@link ControlValue} 的选中项。
 */
public final class WriteDeviceControl {

    private final DevicePort port;

    public WriteDeviceControl(DevicePort port) {
        this.port = port;
    }

    /**
     * {@code capabilityId} 必须非空：能力 id 是 biz 目录里的标识字符串，
     * 空串表示调用方没有拿能力描述就直接写，是上游缺陷，不在端口里静默丢弃。
     */
    public void execute(String capabilityId, ControlValue value,
                        Consumer<DeviceWriteResult> callback) {
        Objects.requireNonNull(capabilityId, "capabilityId");
        if (capabilityId.isEmpty()) {
            throw new IllegalArgumentException("write requires a non-empty capability id");
        }
        port.write(capabilityId, Objects.requireNonNull(value, "value"),
                Objects.requireNonNull(callback, "callback"));
    }
}
