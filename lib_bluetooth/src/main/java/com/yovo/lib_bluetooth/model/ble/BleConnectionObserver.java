package com.yovo.lib_bluetooth.model.ble;

import androidx.annotation.NonNull;

/**
 * BLE 连接与链路参数变更观察者。
 */
public interface BleConnectionObserver {

    default void onConnectionStateChanged(@NonNull BleConnectionSnapshot snapshot) {
    }

    default void onServicesDiscovered(@NonNull BleDeviceId deviceId,
                                      @NonNull BleGattDatabaseSnapshot database) {
    }

    default void onMtuChanged(@NonNull BleDeviceId deviceId, int mtu) {
    }

    default void onPhyUpdated(@NonNull BleDeviceId deviceId, @NonNull BlePhyConfig phy) {
    }

    default void onConnectionUpdated(@NonNull BleDeviceId deviceId,
                                     @NonNull BleConnectionParameters parameters) {
    }

    default void onRssiRead(@NonNull BleDeviceId deviceId, int rssi) {
    }

    default void onBondStateChanged(@NonNull BleDeviceId deviceId,
                                    @NonNull BleBondState bondState) {
    }

    default void onConnectionFailed(@NonNull BleFailure failure) {
    }
}
