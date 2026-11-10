package com.yovo.lib_bluetooth.model.ble;

import androidx.annotation.NonNull;

/**
 * L2CAP CoC 服务端观察者。系统分配 PSM，监听成功后通过 {@link #onListening(int)} 交出。
 */
public interface BleL2capServerObserver {

    void onListening(int psm);

    void onClientAccepted(@NonNull BleDeviceId remoteDeviceId, @NonNull BleL2capChannelHandle channel);

    default void onDataReceived(@NonNull BleDeviceId remoteDeviceId, @NonNull byte[] chunk) {
    }

    default void onClientClosed(@NonNull BleDeviceId remoteDeviceId) {
    }

    default void onServerClosed() {
    }

    default void onFailed(@NonNull BleFailure failure) {
    }
}
