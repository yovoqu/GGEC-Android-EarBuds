package com.yovo.lib_bluetooth.internal.classic.link;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 经典蓝牙 BR/EDR 的 ACL 基础链路状态。ACL CONNECTED 只说明设备间
 * 存在基础传输链路，不代表 A2DP、HEADSET 或 SPP 已连接。
 */

public enum YoCbtAclLinkState {
    UNKNOWN,
    DISCONNECTED,
    CONNECTED,
    DISCONNECT_REQUESTED
}
