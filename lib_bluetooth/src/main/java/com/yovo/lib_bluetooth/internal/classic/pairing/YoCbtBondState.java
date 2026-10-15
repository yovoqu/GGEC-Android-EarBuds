package com.yovo.lib_bluetooth.internal.classic.pairing;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 配对（Bond）状态枚举。
 */
public enum YoCbtBondState {
    UNKNOWN,
    NONE,
    BONDING,
    BONDED,
    REMOVED,
    FAILED
}
