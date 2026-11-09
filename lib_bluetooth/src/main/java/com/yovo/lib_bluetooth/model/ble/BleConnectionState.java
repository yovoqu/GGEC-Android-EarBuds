package com.yovo.lib_bluetooth.model.ble;

/**
 * BLE GATT 连接会话生命周期状态。
 */
public enum BleConnectionState {
    IDLE,
    CONNECTING,
    CONNECTED,
    BONDING_WAIT,
    DISCOVERING_SERVICES,
    INITIALIZING,
    READY,
    DISCONNECTING,
    DISCONNECTED,
    FAILED
}
