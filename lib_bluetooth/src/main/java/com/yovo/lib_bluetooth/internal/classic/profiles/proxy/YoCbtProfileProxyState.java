package com.yovo.lib_bluetooth.internal.classic.profiles.proxy;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description Android {@link android.bluetooth.BluetoothProfile} 服务代理的生命周期状态。
 */
public enum YoCbtProfileProxyState {
    IDLE,
    REQUESTING,
    CONNECTED,
    DISCONNECTED,
    FAILED,
    CLOSING,
    CLOSED
}
