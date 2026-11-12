package com.ggec.earbuds.domain.device.usecase;

import com.ggec.earbuds.domain.device.model.ScanResult;
import com.ggec.earbuds.domain.device.port.DevicePort;

import java.util.List;
import java.util.function.Consumer;

/** 启动 / 停止扫描。过滤与扫描器选择都由 SDK 内部完成。 */
public final class ScanDevices {

    private final DevicePort port;

    public ScanDevices(DevicePort port) {
        this.port = port;
    }

    public void startBatch(Consumer<List<ScanResult>> onResults,
                           Consumer<DevicePort.ScanFailure> onFailed,
                           Runnable onFinished,
                           Consumer<ScanResult> onRemoved) {
        port.startScanBatch(onResults, onFailed, onFinished, onRemoved);
    }

    public void stop() {
        port.stopScan();
    }

    public boolean isScanning() {
        return port.isScanning();
    }
}
