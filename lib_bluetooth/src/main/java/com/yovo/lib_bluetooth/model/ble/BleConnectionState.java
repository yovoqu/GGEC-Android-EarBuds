package com.yovo.lib_bluetooth.model.ble;

/**
 * BLE GATT 连接状态。
 */
public enum BleConnectionState {
    IDLE,
    CONNECTING,
    CONNECTED,
    DISCOVERING_SERVICES,
    READY,
    DISCONNECTING,
    DISCONNECTED,
    FAILED
}
