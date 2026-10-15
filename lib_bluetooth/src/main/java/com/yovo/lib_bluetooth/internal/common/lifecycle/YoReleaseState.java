package com.yovo.lib_bluetooth.internal.common.lifecycle;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 生命周期作用域状态：活跃、释放中、已释放。
 */
public enum YoReleaseState {
    ACTIVE,
    RELEASING,
    RELEASED
}
