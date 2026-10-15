package com.yovo.lib_bluetooth.model.ble;

import androidx.annotation.NonNull;

/**
 * BLE 连接状态观察者。
 */
public interface BleConnectionObserver {

    default void onConnectionStateChanged(@NonNull BleConnectionSnapshot snapshot) {
    }

    default void onConnectionFailed(@NonNull BleFailure failure) {
    }
}
