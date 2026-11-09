package com.yovo.lib_bluetooth.model.ble;

import androidx.annotation.NonNull;

import java.util.List;

/**
 * BLE 扫描结果观察者。所有回调均由 {@code callbackDispatcher} 投递。
 */
public interface BleScanObserver {

    default void onScanStarted() {
    }

    default void onDeviceFound(@NonNull BleScanResult result) {
    }

    default void onBatchScanResults(@NonNull List<BleScanResult> results) {
    }

    default void onScanResultLost(@NonNull BleScanResult lostResult) {
    }

    default void onScanStopped() {
    }

    default void onScanFailed(@NonNull BleFailure failure) {
    }
}
