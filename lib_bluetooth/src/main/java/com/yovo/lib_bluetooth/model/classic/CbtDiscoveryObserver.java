package com.yovo.lib_bluetooth.model.classic;

import androidx.annotation.NonNull;

/**
 * 设备发现观察者。
 */
public interface CbtDiscoveryObserver {

    default void onDiscoveryStateChanged(@NonNull CbtDiscoveryState state) {
    }

    default void onDeviceFound(@NonNull CbtDiscoveryResult result) {
    }

    default void onDiscoveryFailed(@NonNull CbtFailure failure) {
    }
}
