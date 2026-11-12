package com.ggec.earbuds.domain.device.port;

import com.ggec.earbuds.domain.device.model.DeviceSnapshot;
import com.ggec.earbuds.domain.device.model.ScanResult;

import java.util.List;
import java.util.function.Consumer;

/**
 * 设备协议端口。真实实现由 SDK 适配层提供；未接入 SDK 前由闲置空态端口提供实现。
 * APP 只通过该端口表达意图，端口内部如何处理 FeatureCode / market / BLE / SPP 与 APP 无关。
 */
public interface DevicePort {

    enum ScanFailure {
        BLUETOOTH_DISABLED,
        PERMISSION_DENIED,
        LOCATION_DISABLED,
        BLE_UNSUPPORTED,
        SCAN_THROTTLED,
        FAILED
    }

    DeviceSnapshot snapshot();

    Subscription observe(Consumer<DeviceSnapshot> listener);

    /**
     * 用 BLE 扫描地址发起连接。{@code bleName} 是广播本地名，只用于展示，不参与寻址。
     */
    void connect(String modelId, String variantId, String bleMac, String bleName);

    void disconnect();

    /**
     * 启动一次批量扫描：结果按批次回调、失败回调、结束回调（超时/停止/失败均会触发一次）、
     * 设备过期移除回调。这是扫描的唯一入口与唯一签名，四类回调全部必填，
     * 避免“单条/批量/带失败/带移除”多重重载造成的实现分叉。
     */
    void startScanBatch(Consumer<List<ScanResult>> onResults,
                        Consumer<ScanFailure> onFailed,
                        Runnable onFinished,
                        Consumer<ScanResult> onRemoved);

    void stopScan();

    boolean isScanning();

    /**
     * 释放端口持有的底层资源（停止扫描、关闭协议会话）。由装配根在进程生命周期结束时调用。
     */
    void close();
}
