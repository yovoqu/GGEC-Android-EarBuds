package com.yovo.lib_bluetooth.model.ble;

import androidx.annotation.NonNull;

/**
 * GATT 读/写/通知的异步结果。成功与失败都在回调线程投递。
 */
public interface BleGattObserver {

    default void onRead(@NonNull BleDeviceId deviceId,
                        @NonNull BleGattTarget target,
                        @NonNull byte[] value) {
    }

    default void onWrite(@NonNull BleDeviceId deviceId, @NonNull BleGattTarget target) {
    }

    default void onNotifyChanged(@NonNull BleDeviceId deviceId,
                                 @NonNull BleGattTarget target,
                                 boolean enabled) {
    }

    default void onFailed(@NonNull BleFailure failure) {
    }
}
