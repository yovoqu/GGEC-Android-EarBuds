package com.yovo.lib_bluetooth.internal.classic.profiles.a2dp;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtProfileConnectionState;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description A2DP 连接状态不可变快照，记录某时刻设备的连接状态与时间戳。
 */
public final class YoCbtA2dpConnectionSnapshot {

    private final YoCbtDeviceId deviceId;
    private final YoCbtProfileConnectionState state;
    private final long timestamp;

    /**
     * 创建新的连接快照。
     *
     * @param deviceId  捕获连接状态的设备
     * @param state     快照时刻的连接状态
     * @param timestamp 观测到该状态时的 epoch 毫秒时间戳
     */
    public YoCbtA2dpConnectionSnapshot(
            @NonNull YoCbtDeviceId deviceId,
            @NonNull YoCbtProfileConnectionState state,
            long timestamp) {
        this.deviceId = deviceId;
        this.state = state;
        this.timestamp = timestamp;
    }

    /**
     * 返回与此快照关联的设备。
     *
     * @return 设备标识
     */
    @NonNull
    public YoCbtDeviceId getDeviceId() {
        return deviceId;
    }

    /**
     * 返回快照时刻的连接状态。
     *
     * @return 连接状态
     */
    @NonNull
    public YoCbtProfileConnectionState getState() {
        return state;
    }

    /**
     * 返回观测到状态时的 epoch 毫秒时间戳。
     *
     * @return 观测时间戳（毫秒）
     */
    public long getTimestamp() {
        return timestamp;
    }
}
