package com.yovo.lib_bluetooth.model.ble;

import androidx.annotation.NonNull;

/**
 * BLE 外设广播状态观察者。
 */
public interface BleAdvertiseObserver {

    default void onAdvertisingStarted() {
    }

    default void onAdvertisingStopped() {
    }

    default void onAdvertisingFailed(@NonNull BleFailure failure) {
    }
}
