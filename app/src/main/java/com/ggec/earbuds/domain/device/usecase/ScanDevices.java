package com.ggec.earbuds.domain.device.usecase;

import com.ggec.earbuds.domain.device.model.DeviceFailure;
import com.ggec.earbuds.domain.device.model.ScanResult;
import com.ggec.earbuds.domain.device.port.DeviceLinkPort;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 启动 / 停止扫描，以及把机型筛选下发给端口。
 *
 * <p>扫描是全集行为：装配层的网关会启动每个接入端点并把结果按地址去重合并成一批，
 * 因此这里不需要也不允许选择「当前扫描器」。筛选只是下发给 provider 的规则，
 * 切换筛选不重启扫描。</p>
 */
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
