package com.yovo.lib_bluetooth.internal.classic.discovery;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.error.YoCbtFailure;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 设备发现过程的状态与结果回调观察者。
 */
public interface YoCbtDiscoveryObserver {
    default void onDiscoveryStateChanged(@NonNull YoCbtDiscoveryState state) {
    }

    default void onDeviceFound(@NonNull YoCbtDiscoveryResult result) {
    }

    default void onDiscoveryFailed(@NonNull YoCbtFailure failure) {
    }
}
