package com.yovo.lib_bluetooth.model.ble;

import androidx.annotation.NonNull;

/**
 * BLE 设备配对状态变更观察者。
 */
public interface BleBondObserver {

    void onBondStateChanged(@NonNull BleDeviceId deviceId,
                            @NonNull BleBondState previousState,
                            @NonNull BleBondState currentState,
                            int rawReason);
}
