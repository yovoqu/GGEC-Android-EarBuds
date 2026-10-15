package com.yovo.lib_bluetooth.internal.classic.device.snapshot;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.error.YoCbtFailure;

import java.util.List;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 已连接设备列表变化观察者。
 */

public interface YoCbtConnectedDeviceObserver {
    void onConnectedDevices(@NonNull List<YoCbtDeviceSnapshot> snapshots);

    void onConnectedDeviceQueryFailed(@NonNull YoCbtFailure failure);
}
