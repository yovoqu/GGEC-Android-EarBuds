package com.yovo.lib_bluetooth.internal.classic.pairing;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.device.snapshot.YoCbtDeviceSnapshot;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 配对状态变化观察者回调接口。
 */
public interface YoCbtBondStateObserver {
    void onBondStateChanged(@NonNull YoCbtDeviceSnapshot snapshot);
}
