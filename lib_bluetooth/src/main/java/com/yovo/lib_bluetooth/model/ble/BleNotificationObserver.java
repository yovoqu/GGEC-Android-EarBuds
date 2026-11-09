package com.yovo.lib_bluetooth.model.ble;

import androidx.annotation.NonNull;

/**
 * 独立多播 Notification / Indication 订阅观察者（支持引用计数共享 CCCD）。
 */
public interface BleNotificationObserver {

    default void onNotificationEnabled(@NonNull BleDeviceId deviceId, @NonNull BleGattTarget target) {
    }

    default void onNotificationDisabled(@NonNull BleDeviceId deviceId, @NonNull BleGattTarget target) {
    }

    void onCharacteristicChanged(@NonNull BleDeviceId deviceId,
                                 @NonNull BleGattTarget target,
                                 @NonNull byte[] value);

    default void onNotificationFailed(@NonNull BleFailure failure) {
    }
}
