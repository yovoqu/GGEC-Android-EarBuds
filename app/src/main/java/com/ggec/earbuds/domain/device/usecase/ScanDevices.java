package com.ggec.earbuds.domain.device.usecase;

import com.ggec.earbuds.domain.device.model.DeviceFailure;
import com.ggec.earbuds.domain.device.model.ScanResult;
import com.ggec.earbuds.domain.device.port.DeviceLinkPort;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** 启动 / 停止扫描。过滤与扫描器选择都由 SDK 内部完成。 */
public final class ScanDevices {

    private final DeviceLinkPort port;

    public ScanDevices(DeviceLinkPort port) {
        this.port = port;
    }

    public void startBatch(Consumer<List<ScanResult>> onResults,
                           Consumer<DeviceFailure> onFailed,
                           Runnable onFinished,
                           Consumer<ScanResult> onRemoved) {
        port.startScanBatch(onResults, onFailed, onFinished, onRemoved);
    }

    public void filterModels(Set<String> modelIds) {
        port.filterModels(modelIds);
    }

    public void stop() {
        port.stopScan();
    }

    public boolean isScanning() {
        return port.isScanning();
    }
}
