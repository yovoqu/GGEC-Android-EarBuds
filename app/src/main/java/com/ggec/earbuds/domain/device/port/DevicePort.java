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

    void connect(String modelId, String variantId, String mac);

    void disconnect();

    void startScan(Consumer<ScanResult> onResult, Runnable onFinished);

    default void startScan(Consumer<ScanResult> onResult,
                           Consumer<ScanFailure> onFailed,
                           Runnable onFinished) {
        startScan(onResult, onFinished);
    }

    default void startScanBatch(Consumer<List<ScanResult>> onResults,
                                Consumer<ScanFailure> onFailed,
                                Runnable onFinished) {
        startScan(
                result -> {
                    if (onResults != null && result != null) {
                        onResults.accept(List.of(result));
                    }
                },
                onFailed,
                onFinished);
    }

    void stopScan();

    boolean isScanning();
}
