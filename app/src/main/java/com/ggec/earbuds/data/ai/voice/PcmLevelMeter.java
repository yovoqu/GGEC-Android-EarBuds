package com.ggec.earbuds.data.ai.voice;

import androidx.annotation.Nullable;

/**
 * 纯 PCM16 单声道小端字节块的 dBFS 归一化电平计。
 *
 * <p>单一职责：根据采样瞬时均方根能量计算 {@code [0, 1]} 对数电平供界面声场动效使用，
 * 与硬件设备采集完全解耦，使得内置麦、蓝牙麦或外部推流输入源无需各自重复计算电平。</p>
 */
final class PcmLevelMeter {

    private static final float MIN_DB = -55f;
    private static final float MAX_DB = -5f;
    private static final float MIN_VISIBLE_LEVEL = 0.02f;
    private static final double MAX_AMPLITUDE = 32_768.0;

    private PcmLevelMeter() {
    }

    /** 计算一帧 PCM16 数据的归一化响度 {@code 0..1}；全静音返回 0。 */
    static float levelOf(@Nullable byte[] pcm16) {
        if (pcm16 == null || pcm16.length < 2) {
            return 0f;
        }
        int samples = pcm16.length / 2;
        double sumSquares = 0.0;
        for (int i = 0; i < samples * 2; i += 2) {
            int sample = (short) ((pcm16[i] & 0xFF) | (pcm16[i + 1] << 8));
            sumSquares += (double) sample * sample;
        }
        if (sumSquares == 0.0) {
            return 0f;
        }
        double rms = Math.sqrt(sumSquares / samples);
        double dbfs = 20.0 * Math.log10(rms / MAX_AMPLITUDE);
        float normalized = (float) ((dbfs - MIN_DB) / (MAX_DB - MIN_DB));
        return Math.max(MIN_VISIBLE_LEVEL, Math.min(1f, normalized));
    }
}
