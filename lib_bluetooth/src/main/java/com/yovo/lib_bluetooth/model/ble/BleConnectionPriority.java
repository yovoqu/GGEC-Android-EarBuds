package com.yovo.lib_bluetooth.model.ble;

/**
 * BLE 连接参数优先级（对齐 Android BluetoothGatt CONNECTION_PRIORITY_*）。
 */
public enum BleConnectionPriority {
    BALANCED,
    HIGH,
    LOW_POWER,
    DCK
}
