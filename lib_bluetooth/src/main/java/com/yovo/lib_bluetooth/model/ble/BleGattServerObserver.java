package com.yovo.lib_bluetooth.model.ble;

import androidx.annotation.NonNull;

/**
 * 本地托管 GATT Server 事件观察者。
 */
public interface BleGattServerObserver {

    default void onServerStarted() {
    }

    default void onServerStopped() {
    }

    default void onClientConnected(@NonNull BleDeviceId clientDeviceId) {
    }

    default void onClientDisconnected(@NonNull BleDeviceId clientDeviceId) {
    }

    default void onClientReadCharacteristic(@NonNull BleDeviceId clientDeviceId,
                                            @NonNull BleGattTarget target,
                                            @NonNull byte[] value) {
    }

    default void onClientWriteCharacteristic(@NonNull BleDeviceId clientDeviceId,
                                             @NonNull BleGattTarget target,
                                             @NonNull byte[] value,
                                             boolean withResponse) {
    }

    default void onClientSubscriptionChanged(@NonNull BleDeviceId clientDeviceId,
                                             @NonNull BleGattTarget target,
                                             boolean notifyEnabled,
                                             boolean indicateEnabled) {
    }

    default void onNotificationSent(@NonNull BleDeviceId clientDeviceId, int rawStatus) {
    }

    default void onMtuChanged(@NonNull BleDeviceId clientDeviceId, int mtu) {
    }

    default void onFailed(@NonNull BleFailure failure) {
    }
}
