package com.yovo.lib_bluetooth.internal.classic.profiles.state;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 单个 Profile 的独立连接状态。CONNECTED 必须来自该 Profile 的系统
 * 代理、系统广播或 SPP socket 注册表；不得由 ACL CONNECTED 或 SDP 能力推导。
 */

public enum YoCbtProfileConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    UNKNOWN,
    ERROR
}
