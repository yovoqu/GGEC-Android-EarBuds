package com.yovo.lib_bluetooth.model.classic;

import androidx.annotation.NonNull;

import java.util.List;

/**
 * 已连接设备查询观察者。
 */
public interface CbtConnectedDeviceObserver {

    void onConnectedDevices(@NonNull List<CbtDeviceSnapshot> snapshots);

    void onConnectedDeviceQueryFailed(@NonNull CbtFailure failure);
}
