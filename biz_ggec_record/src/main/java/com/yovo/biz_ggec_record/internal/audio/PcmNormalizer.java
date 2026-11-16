package com.yovo.biz_ggec_record.internal.audio;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 把 PCM 16-bit 小端数据下混为单声道，并以连续线性插值重采样到 16 kHz。
 */
public final class PcmNormalizer {

    public static final int TARGET_SAMPLE_RATE = 16_000;

    private int configuredRate;
    @Nullable
    private Float tail;
    private double phase;

    public void reset() {
        configuredRate = 0;
        tail = null;
        phase = 0.0;
    }

    @NonNull
    public float[] normalize(@NonNull byte[] pcm, int sampleRate, int channels) {
        if (sampleRate <= 0 || channels < 1 || channels > 2) {
            throw new IllegalArgumentException("invalid pcm config");
        }
        int frames = pcm.length / (channels * 2);
        float[] mono = new float[frames];
        int offset = 0;
        for (int i = 0; i < frames; i++) {
            int sum = 0;
            for (int c = 0; c < channels; c++) {
                int lo = pcm[offset] & 0xff;
                int hi = pcm[offset + 1];
                sum += (short) ((hi << 8) | lo);
                offset += 2;
            }
            mono[i] = sum / (float) channels / 32768.0f;
        }
        return resample(mono, sampleRate);
    }

    @NonNull
    private float[] resample(@NonNull float[] input, int sampleRate) {
        if (configuredRate != sampleRate) {
            configuredRate = sampleRate;
            tail = null;
            phase = 0.0;
        }
        if (input.length == 0 || sampleRate == TARGET_SAMPLE_RATE) {
            return input;
        }

        Float previous = tail;
        float[] data;
        if (previous == null) {
            data = input;
        } else {
            data = new float[input.length + 1];
            data[0] = previous;
            System.arraycopy(input, 0, data, 1, input.length);
        }
        double step = sampleRate / (double) TARGET_SAMPLE_RATE;
        float[] output = new float[(int) Math.ceil(data.length / step) + 2];
        int count = 0;
        double position = phase;
        while (position + 1.0 < data.length) {
            int index = (int) position;
            float fraction = (float) (position - index);
            output[count++] = data[index] * (1.0f - fraction) + data[index + 1] * fraction;
            position += step;
        }
        tail = data[data.length - 1];
        phase = position - (data.length - 1);
        float[] trimmed = new float[count];
        System.arraycopy(output, 0, trimmed, 0, count);
        return trimmed;
    }
}
