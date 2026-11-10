package com.yovo.lib_bluetooth.model.ble;

import androidx.annotation.NonNull;

/**
 * 本机蓝牙适配器状态观察者。回调在配置的 callback 线程。
 */
public interface BleAdapterStateObserver {

    void onAdapterStateChanged(@NonNull BleAdapterState previous, @NonNull BleAdapterState current);
}
