package com.yovo.lib_bluetooth.internal.classic.profiles.headset;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.common.result.YoResult;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.profiles.headset.YoCbtHeadsetAudioState;
import com.yovo.lib_bluetooth.internal.classic.profiles.headset.YoCbtHeadsetDeviceState;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description Headset/HFP Profile 状态查询接口，从代理或广播缓存读取设备状态。
 */
public interface YoCbtHeadsetStateProvider {

    /**
     * 返回给定设备当前的 Headset 设备状态。
     *
     * <p>状态包含 Profile 连接状态（已连接、连接中等）、
     * 音频/SCO 状态、语音识别状态及最近一次已知变更的时间戳。
     *
     * @param deviceId 目标设备
     * @return 成功时包含设备状态的 {@link YoResult}；
     *         无法确定状态时返回失败
     */
    @NonNull
    YoResult<YoCbtHeadsetDeviceState> getDeviceState(@NonNull YoCbtDeviceId deviceId);

    /**
     * 返回给定设备当前的音频（SCO）状态。
     *
     * <p>音频状态反映本机与耳机之间是否已建立 SCO（同步面向连接）音频链路。
     *
     * @param deviceId 目标设备
     * @return 成功时包含音频状态的 {@link YoResult}；
     *         无法确定音频状态时返回失败
     */
    @NonNull
    YoResult<YoCbtHeadsetAudioState> getAudioState(@NonNull YoCbtDeviceId deviceId);

    /**
     * 返回当前通过 Headset/HFP Profile 连接的设备数量。
     *
     * @return 成功时包含已连接设备数量的 {@link YoResult}；
     *         无法获取数量时返回失败
     */
    @NonNull
    YoResult<Integer> getConnectedDeviceCount();
}
