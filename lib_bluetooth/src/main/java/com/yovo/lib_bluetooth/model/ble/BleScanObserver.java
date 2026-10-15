package com.yovo.lib_bluetooth.model.ble;

import androidx.annotation.NonNull;

/**
 * BLE 扫描结果观察者。
 */
public interface BleScanObserver {

    default void onScanStarted() {
    }

    default void onDeviceFound(@NonNull BleScanResult result) {
    }

    default void onScanStopped() {
    }

    default void onScanFailed(@NonNull BleFailure failure) {
    }
}
