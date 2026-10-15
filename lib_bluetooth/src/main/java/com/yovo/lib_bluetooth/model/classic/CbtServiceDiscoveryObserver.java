package com.yovo.lib_bluetooth.model.classic;

import androidx.annotation.NonNull;

/**
 * SDP 服务发现观察者。
 */
public interface CbtServiceDiscoveryObserver {

    default void onStateChanged(@NonNull CbtServiceDiscoveryState state) {
    }

    default void onResolved(@NonNull CbtServiceDiscoverySnapshot snapshot) {
    }

    default void onFailed(@NonNull CbtFailure failure) {
    }
}
