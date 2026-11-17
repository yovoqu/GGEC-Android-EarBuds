package com.ggec.earbuds.domain.device.model;

import androidx.annotation.NonNull;

/**
 * 随录随传会话状态。
 *
 * <p>只承载协议真实上报的三个值：阶段、时长、当前电平。落盘文件名不在本状态里——它由
 * {@link SavedRecordItem} 在 {@code onFileSaved} 时承载，本状态若再带一份可空文件名，
 * 就等于允许「会话状态说有个文件、文件列表里却没有」的黑洞。</p>
 */
public record StreamAudioSessionState(
        @NonNull Status status,
        long durationMs,
        float currentAmplitude
) {
    public enum Status {
        IDLE,
        STARTING,
        RECORDING,
        STOPPING
    }

    public static StreamAudioSessionState idle() {
        return new StreamAudioSessionState(Status.IDLE, 0, 0f);
    }
}
