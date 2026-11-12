package com.ggec.earbuds.domain.device.model;

/** 设备会话状态。与设备层会话一致：空闲、连接中、就绪、已断开。 */
public enum ConnectionState {
    IDLE,
    CONNECTING,
    READY,
    DISCONNECTED;

    public boolean isConnected() {
        return this == READY;
    }
}
