package com.ggec.earbuds.domain.device.model;

/** 连接状态机（APP 视角）。 */
public enum ConnectionState {
    IDLE,
    SCANNING,
    CONNECTING,
    CONNECTED_NOT_READY,
    READY,
    DISCONNECTED;

    public boolean isConnected() {
        return this == CONNECTED_NOT_READY || this == READY;
    }
}
