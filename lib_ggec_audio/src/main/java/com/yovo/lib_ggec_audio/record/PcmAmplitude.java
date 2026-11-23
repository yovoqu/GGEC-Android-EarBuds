package com.yovo.lib_ggec_audio.record;

import androidx.annotation.Nullable;

/**
 * 16-bit 单声道 PCM 振幅计算：录音波形观测的唯一实现。
 */
public final class PcmAmplitude {

    private PcmAmplitude() {
    }

    /**
     * 归一化平均振幅。
     *
     * @param pcmData 16-bit 小端 PCM 数据；{@code null} 或不足一个采样点时返回 0
     * @return 0.0f ~ 1.0f
     */
    public static float normalized(@Nullable byte[] pcmData) {
        if (pcmData == null || pcmData.length < 2) {
            return 0f;
        }
        long sum = 0;
        int count = pcmData.length / 2;
        for (int i = 0; i < pcmData.length - 1; i += 2) {
            short sample = (short) ((pcmData[i] & 0xFF) | (pcmData[i + 1] << 8));
            sum += Math.abs(sample);
        }
        float avg = (float) sum / count;
        return Math.min(1.0f, avg / 32767f);
    }
}
