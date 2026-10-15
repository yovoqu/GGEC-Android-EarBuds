package com.yovo.lib_bluetooth.internal.classic.profiles.headset;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.profiles.headset.YoCbtHeadsetAudioState;
import com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtProfileConnectionState;

import java.util.Objects;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description Headset/HFP 连接状态不可变快照，含 Profile 连接与 SCO 音频状态。
 */
public final class YoCbtHeadsetConnectionSnapshot {

    private final YoCbtDeviceId deviceId;
    private final YoCbtProfileConnectionState profileState;
    private final YoCbtHeadsetAudioState audioState;
    private final long timestamp;

    /**
     * 创建新的 Headset 连接快照。
     *
     * @param deviceId     设备标识（不可为 null）
     * @param profileState Profile 连接状态（不可为 null）
     * @param audioState   音频/SCO 状态（不可为 null）
     * @param timestamp    捕获此快照时的毫秒时间戳
     */
    public YoCbtHeadsetConnectionSnapshot(@NonNull YoCbtDeviceId deviceId,
                                          @NonNull YoCbtProfileConnectionState profileState,
                                          @NonNull YoCbtHeadsetAudioState audioState,
                                          long timestamp) {
        this.deviceId = deviceId;
        this.profileState = profileState;
        this.audioState = audioState;
        this.timestamp = timestamp;
    }

    /**
     * 返回与此快照关联的设备标识。
     *
     * @return 设备 ID
     */
    @NonNull
    public YoCbtDeviceId getDeviceId() {
        return deviceId;
    }

    /**
     * 返回快照时刻的 Profile 连接状态。
     *
     * @return Profile 连接状态
     */
    @NonNull
    public YoCbtProfileConnectionState getProfileState() {
        return profileState;
    }

    /**
     * 返回快照时刻的音频（SCO）状态。
     *
     * @return 音频状态
     */
    @NonNull
    public YoCbtHeadsetAudioState getAudioState() {
        return audioState;
    }

    /**
     * 返回捕获此快照时的毫秒时间戳。
     *
     * @return 捕获时间戳
     */
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof YoCbtHeadsetConnectionSnapshot)) return false;
        YoCbtHeadsetConnectionSnapshot that = (YoCbtHeadsetConnectionSnapshot) o;
        return timestamp == that.timestamp
                && deviceId.equals(that.deviceId)
                && profileState == that.profileState
                && audioState == that.audioState;
    }

    @Override
    public int hashCode() {
        return Objects.hash(deviceId, profileState, audioState, timestamp);
    }

    @Override
    @NonNull
    public String toString() {
        return "YoCbtHeadsetConnectionSnapshot{"
                + "deviceId=" + deviceId
                + ", profileState=" + profileState
                + ", audioState=" + audioState
                + ", timestamp=" + timestamp
                + '}';
    }
}
