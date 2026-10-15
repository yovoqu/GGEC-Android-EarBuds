package com.yovo.lib_bluetooth.internal.classic.profiles.core;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description Profile 子系统领域事件（代理生命周期、状态刷新等）。
 */
public enum YoCbtProfileEvent {
    PROXY_REQUESTED,
    PROXY_READY,
    PROXY_LOST,
    AGGREGATE_STATE_CHANGED,
    DEVICE_STATE_CHANGED,
    EXTENSION_STATE_CHANGED
}
