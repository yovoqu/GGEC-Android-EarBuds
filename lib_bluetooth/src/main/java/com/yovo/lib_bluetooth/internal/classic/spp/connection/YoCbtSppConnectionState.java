package com.yovo.lib_bluetooth.internal.classic.spp.connection;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description SDK 自有 RFCOMM socket 的生命周期。CONNECTED 仅在 socket、输入输出流、
 * reader 与 writer 全部启动成功后产生；FAILED/DISCONNECTED/RELEASED 均不是连接态。
 */

public enum YoCbtSppConnectionState {
    IDLE,
    PREPARING,
    RESOLVING_SERVICE,
    CREATING_SOCKET,
    CONNECTING_SOCKET,
    OPENING_STREAM,
    STARTING_READER,
    STARTING_WRITER,
    CONNECTED,
    DISCONNECTING,
    DISCONNECTED,
    FAILED,
    RELEASED;

    public boolean isConnecting() {
        return this == PREPARING
                || this == RESOLVING_SERVICE
                || this == CREATING_SOCKET
                || this == CONNECTING_SOCKET
                || this == OPENING_STREAM
                || this == STARTING_READER
                || this == STARTING_WRITER;
    }

    public boolean isConnected() {
        return this == CONNECTED;
    }

    public boolean isTerminal() {
        return this == DISCONNECTED || this == FAILED || this == RELEASED;
    }
}
