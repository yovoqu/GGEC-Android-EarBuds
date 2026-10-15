package com.yovo.lib_bluetooth.internal.common.bluetooth;

import androidx.annotation.NonNull;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 蓝牙适配器电源状态变化观察者。
 */

public interface YoBluetoothStateObserver {
    void onBluetoothStateChanged(@NonNull YoBluetoothPowerState state);
}
