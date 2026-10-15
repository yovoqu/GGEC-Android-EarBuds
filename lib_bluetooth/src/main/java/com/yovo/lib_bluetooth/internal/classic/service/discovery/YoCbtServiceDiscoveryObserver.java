package com.yovo.lib_bluetooth.internal.classic.service.discovery;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.device.snapshot.YoCbtDeviceSnapshot;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtFailure;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 服务发现过程的状态与结果回调观察者。
 */
public interface YoCbtServiceDiscoveryObserver {
    default void onServiceDiscoveryStateChanged(@NonNull YoCbtServiceDiscoveryState state) {
    }

    default void onServiceDiscoveryStarted() {
    }

    default void onServiceDiscoveryResolved(@NonNull YoCbtDeviceSnapshot snapshot) {
    }

    default void onServiceDiscoveryFailed(@NonNull YoCbtFailure failure) {
    }
}
