package com.yovo.lib_bluetooth.model.ble;

import androidx.annotation.NonNull;

/**
 * GATT 读/写/描述符/通知与 ReliableWrite 异步结果观察者。
 */
public interface BleGattObserver {

    default void onRead(@NonNull BleDeviceId deviceId,
                        @NonNull BleGattTarget target,
                        @NonNull byte[] value) {
    }

    default void onWrite(@NonNull BleDeviceId deviceId, @NonNull BleGattTarget target) {
    }

    default void onDescriptorRead(@NonNull BleDeviceId deviceId,
                                  @NonNull BleGattTarget target,
                                  @NonNull byte[] value) {
    }

    default void onDescriptorWrite(@NonNull BleDeviceId deviceId,
                                   @NonNull BleGattTarget target) {
    }

    default void onNotifyChanged(@NonNull BleDeviceId deviceId,
                                 @NonNull BleGattTarget target,
                                 boolean enabled) {
    }

    default void onCharacteristicChanged(@NonNull BleDeviceId deviceId,
                                         @NonNull BleGattTarget target,
                                         @NonNull byte[] value) {
    }

    default void onReliableWriteCompleted(@NonNull BleDeviceId deviceId, boolean committed) {
    }

    default void onFailed(@NonNull BleFailure failure) {
    }
}
