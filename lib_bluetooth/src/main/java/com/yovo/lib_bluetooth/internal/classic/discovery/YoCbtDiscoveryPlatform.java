package com.yovo.lib_bluetooth.internal.classic.discovery;

import android.content.Context;

import androidx.annotation.NonNull;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 经典蓝牙发现启停与适配器状态的平台端口。
 */
public interface YoCbtDiscoveryPlatform {

    boolean isSupported();

    boolean isEnabled();

    boolean isDiscovering();

    boolean startDiscovery();

    void cancelDiscovery();

    /**
     * 本机是否还要求应用声明位置权限才能开始经典蓝牙发现（现代版本不再要求）。
     */
    boolean requiresLocationPermission();

    /** @return 发现所需的位置权限是否已授予 */
    boolean hasLocationPermission(@NonNull Context context);
}
