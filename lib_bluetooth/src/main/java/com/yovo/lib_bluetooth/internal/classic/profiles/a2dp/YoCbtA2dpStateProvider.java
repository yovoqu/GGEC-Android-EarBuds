package com.yovo.lib_bluetooth.internal.classic.profiles.a2dp;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.common.result.YoResult;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.profiles.a2dp.YoCbtA2dpDeviceState;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description A2DP Profile 状态查询接口，从 Android 代理读取并映射为 SDK 领域类型。
 */
public interface YoCbtA2dpStateProvider {

    /**
     * 返回给定设备的完整 A2DP 设备状态。
     *
     * <p>返回的状态包含 Profile 连接状态、运行时状态及最近一次变更的时间戳。
     * 若 A2DP Profile 未知该设备，则返回失败结果。
     *
     * @param deviceId 要查询的设备
     * @return 成功时包含 {@link YoCbtA2dpDeviceState} 的 {@link YoResult}；
     *         设备未知或代理不可用时返回失败
     */
    @NonNull
    YoResult<YoCbtA2dpDeviceState> getDeviceState(@NonNull YoCbtDeviceId deviceId);

    /**
     * 检查给定设备是否为当前活跃的 A2DP 设备。
     *
     * <p>活跃设备指当前通过 A2DP 播放音频的设备。
     * Android 11+ 上通过 {@link android.bluetooth.BluetoothA2dp#getActiveDevice()} 查询。
     *
     * @param deviceId 要检查的设备
     * @return 设备活跃时为 {@code YoResult.success(true)}；
     *         已连接但非活跃时为 {@code YoResult.success(false)}；
     *         代理不可用时返回失败
     */
    @NonNull
    YoResult<Boolean> isActiveDevice(@NonNull YoCbtDeviceId deviceId);

    /**
     * 返回当前通过 A2DP Profile 连接的设备数量。
     *
     * <p>包含 {@link android.bluetooth.BluetoothA2dp#getConnectedDevices()}
     * 报告的 {@code STATE_CONNECTED} 状态的所有设备。
     *
     * @return 成功时包含已连接设备数量的 {@link YoResult}；
     *         代理不可用时返回失败
     */
    @NonNull
    YoResult<Integer> getConnectedDeviceCount();
}
