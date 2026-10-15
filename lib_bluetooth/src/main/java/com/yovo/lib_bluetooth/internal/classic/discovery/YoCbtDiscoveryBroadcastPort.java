package com.yovo.lib_bluetooth.internal.classic.discovery;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 设备发现广播端口，接收系统 ACTION_FOUND 等扫描广播。
 */
public interface YoCbtDiscoveryBroadcastPort {

    interface Listener {
        void onDiscoveryStarted();

        void onDiscoveryFinished();

        void onDeviceFound(@NonNull YoCbtDiscoveryDeviceFound found);
    }

    void setListener(@Nullable Listener listener);
}
