package com.yovo.lib_bluetooth.internal.classic.profiles.a2dp;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.profiles.a2dp.YoCbtA2dpDeviceState;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description A2DP Profile 状态变化观察者。
 */
public interface YoCbtA2dpStateObserver {

    /**
     * 当设备 A2DP 状态发生变化时调用。
     *
     * <p>提供的 {@link YoCbtA2dpDeviceState} 反映变更时刻的完整状态快照，
     * 包含连接状态、运行时状态及变更时间戳。
     *
     * <p>观察者在回调执行器线程（通常为主线程）上收到通知。
     * 实现中不应在此回调里执行耗时操作。
     *
     * @param state 新的 A2DP 设备状态
     */
    void onA2dpStateChanged(@NonNull YoCbtA2dpDeviceState state);
}
