package com.yovo.lib_bluetooth.internal.classic.profiles.headset;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.profiles.headset.YoCbtHeadsetDeviceState;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description Headset/HFP Profile 状态变化观察者。
 */
public interface YoCbtHeadsetStateObserver {

    /**
     * 当设备 Headset 状态发生变化时调用。
     *
     * <p>提供的 {@link YoCbtHeadsetDeviceState} 包含变更时刻的完整设备状态快照，
     * 含 Profile 连接状态、音频状态、语音识别状态及状态更新来源。
     *
     * @param state 新的 Headset 设备状态
     */
    void onHeadsetStateChanged(@NonNull YoCbtHeadsetDeviceState state);
}
