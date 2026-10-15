package com.yovo.lib_bluetooth.internal.classic.profiles.headset;

import com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtProfileConnectionState;
import com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtProfileStateSource;
import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 单设备 Headset/HFP 完整状态快照。
 */

public final class YoCbtHeadsetDeviceState {
    private final YoCbtDeviceId deviceId;
    private final YoCbtProfileConnectionState profileState;
    private final YoCbtHeadsetAudioState audioState;
    private final boolean voiceRecognitionActive;
    private final long lastChangedAtMillis;
    private final YoCbtProfileStateSource source;

    public YoCbtHeadsetDeviceState(@NonNull YoCbtDeviceId deviceId,
                                   @NonNull YoCbtProfileConnectionState profileState,
                                   @NonNull YoCbtHeadsetAudioState audioState,
                                   boolean voiceRecognitionActive,
                                   long lastChangedAtMillis,
                                   @NonNull YoCbtProfileStateSource source) {
        this.deviceId = deviceId;
        this.profileState = profileState;
        this.audioState = audioState;
        this.voiceRecognitionActive = voiceRecognitionActive;
        this.lastChangedAtMillis = lastChangedAtMillis;
        this.source = source;
    }

    @NonNull
    public YoCbtDeviceId getDeviceId() {
        return deviceId;
    }

    @NonNull
    public YoCbtProfileConnectionState getProfileState() {
        return profileState;
    }

    @NonNull
    public YoCbtHeadsetAudioState getAudioState() {
        return audioState;
    }

    public boolean isVoiceRecognitionActive() {
        return voiceRecognitionActive;
    }

    public long getLastChangedAtMillis() {
        return lastChangedAtMillis;
    }

    @NonNull
    public YoCbtProfileStateSource getSource() {
        return source;
    }
}
