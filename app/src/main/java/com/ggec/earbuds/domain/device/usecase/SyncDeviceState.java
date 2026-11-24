package com.ggec.earbuds.domain.device.usecase;

import com.ggec.earbuds.domain.device.model.DeviceWriteResult;
import com.ggec.earbuds.domain.device.port.DeviceLinkPort;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * 连接设备后同步设备状态（电量、固件版本、配置快照等）的领域用例。
 *
 * <p>经 {@link DeviceLinkPort#syncState} 触达链路，屏蔽各 SDK 与硬件协议实现细节
 * （例如 HS01 走 98 聚合命令，HS02 走 First/Second 聚合命令）。</p>
 */
public final class SyncDeviceState {

    private final DeviceLinkPort port;

    public SyncDeviceState(DeviceLinkPort port) {
        this.port = Objects.requireNonNull(port, "port");
    }

    public void execute(Consumer<DeviceWriteResult> callback) {
        Objects.requireNonNull(callback, "callback");
        port.syncState(callback);
    }
}
