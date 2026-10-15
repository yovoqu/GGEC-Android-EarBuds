package com.yovo.lib_bluetooth.internal.classic.profiles.headset;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description SCO 音频状态跟踪器，记录各设备 SCO 连接状态变化。
 */

public final class YoCbtScoAudioStateTracker {

    private final Map<String, YoCbtHeadsetAudioState> deviceAudioStates = new ConcurrentHashMap<>();

    public void recordAudioState(@NonNull YoCbtDeviceId deviceId,
                                 @NonNull YoCbtHeadsetAudioState audioState) {
        deviceAudioStates.put(deviceId.value(), audioState);
    }

    @NonNull
    public YoCbtHeadsetAudioState getAudioState(@NonNull YoCbtDeviceId deviceId) {
        YoCbtHeadsetAudioState state = deviceAudioStates.get(deviceId.value());
        return state != null ? state : YoCbtHeadsetAudioState.UNKNOWN;
    }
}
