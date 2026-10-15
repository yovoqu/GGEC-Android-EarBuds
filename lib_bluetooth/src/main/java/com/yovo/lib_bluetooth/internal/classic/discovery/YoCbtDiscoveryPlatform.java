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

    boolean requiresLegacyLocationPermission();

    /** @return Android 12 以下设备是否已授予旧版位置权限 */
    boolean hasLegacyLocationPermission(@NonNull Context context);
}
