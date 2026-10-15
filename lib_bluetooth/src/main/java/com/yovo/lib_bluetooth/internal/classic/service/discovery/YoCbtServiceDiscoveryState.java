package com.yovo.lib_bluetooth.internal.classic.service.discovery;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 服务发现会话的生命周期状态枚举。
 */
public enum YoCbtServiceDiscoveryState {
    IDLE,
    PREPARING,
    DISCOVERING,
    RESOLVED,
    EMPTY,
    FAILED,
    TIMEOUT,
    RELEASED
}
