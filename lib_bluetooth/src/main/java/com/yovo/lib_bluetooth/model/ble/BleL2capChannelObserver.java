package com.yovo.lib_bluetooth.model.ble;

import androidx.annotation.NonNull;

/**
 * L2CAP CoC 通道事件观察者。
 */
public interface BleL2capChannelObserver {

    void onChannelOpened(@NonNull BleL2capChannelHandle channel);

    default void onDataReceived(@NonNull byte[] chunk) {
    }

    default void onChannelClosed() {
    }

    default void onFailed(@NonNull BleFailure failure) {
    }
}
