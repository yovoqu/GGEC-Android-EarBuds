package com.ggec.earbuds.domain.device.port;

import com.ggec.earbuds.domain.device.model.DeviceSnapshot;
import com.ggec.earbuds.domain.device.model.ScanResult;

import java.util.function.Consumer;

/**
 * 设备协议端口。真实实现由 SDK 适配层提供；首版使用假数据实现。
 * APP 只通过该端口表达意图，端口内部如何处理 FeatureCode / market / BLE / SPP 与 APP 无关。
 */
public interface DevicePort {

    DeviceSnapshot snapshot();

    Subscription observe(Consumer<DeviceSnapshot> listener);

    void connect(String modelId, String variantId, String mac);

    void disconnect();

    void startScan(Consumer<ScanResult> onResult, Runnable onFinished);

    void stopScan();

    boolean isScanning();
}
