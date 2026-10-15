package com.yovo.lib_bluetooth.model.classic;

/**
 * SPP 连接状态。
 */
public enum CbtSppConnectionState {
    IDLE,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    DISCONNECTED,
    FAILED;

    public boolean isConnecting() {
        return this == CONNECTING;
    }
}
