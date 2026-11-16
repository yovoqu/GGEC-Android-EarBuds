package com.ggec.earbuds.domain.device.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 随录随传会话状态。
 */
public record StreamAudioSessionState(
        @NonNull Status status,
        long durationMs,
        float currentAmplitude,
        @Nullable String currentFileName
) {
    public enum Status {
        IDLE,
        STARTING,
        RECORDING,
        STOPPING
    }

    public static StreamAudioSessionState idle() {
        return new StreamAudioSessionState(Status.IDLE, 0, 0f, null);
    }
}
