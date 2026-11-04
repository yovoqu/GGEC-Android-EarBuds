package com.ggec.earbuds.domain.device.usecase;

import com.ggec.earbuds.domain.device.model.ScanResult;
import com.ggec.earbuds.domain.device.port.DevicePort;

import java.util.function.Consumer;

/** 启动 / 停止扫描。过滤与扫描器选择都由 SDK 内部完成。 */
public final class ScanDevices {

    private final DevicePort port;

    public ScanDevices(DevicePort port) {
        this.port = port;
    }

    public void start(Consumer<ScanResult> onResult, Runnable onFinished) {
        port.startScan(onResult, onFinished);
    }

    public void stop() {
        port.stopScan();
    }

    public boolean isScanning() {
        return port.isScanning();
    }
}
