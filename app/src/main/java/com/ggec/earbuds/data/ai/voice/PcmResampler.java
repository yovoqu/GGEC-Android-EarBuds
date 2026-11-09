package com.ggec.earbuds.data.ai.voice;

import androidx.annotation.NonNull;

import java.util.Arrays;

/**
 * 通用有理数比值 PCM16 单声道小端流式重采样器。
 *
 * <p>特性：</p>
 * <ul>
 *   <li>任意正采样率自适应（8k / 16k / 24k / 44.1k / 48k 等），支持通话中热切换输入源速率；</li>
 *   <li>同速率零拷贝直通（仅在奇数长度时裁掉末尾半个残余字节）；</li>
 *   <li>基于最大公约数精确整数相位步进，消除连续多帧后的浮点累积偏移；</li>
 *   <li>降采样时采用窗内均值抗混叠滤波，升采样时采用线性插值并钳位在 short 安全区间。</li>
 * </ul>
 */
final class PcmResampler {

    private static final byte[] EMPTY = new byte[0];
    private static final int MIN_SAMPLE = Short.MIN_VALUE;
    private static final int MAX_SAMPLE = Short.MAX_VALUE;

    private int lastInputRateHz;
    private int lastOutputRateHz;
    private long phaseUnitsPerSample;
    private long phaseStep;
    private long phaseUnits;

    /**
     * 将输入 PCM16 从 {@code inputRateHz} 重采样到 {@code outputRateHz}。
     */
    @NonNull
    byte[] resample(@NonNull byte[] pcm16, int inputRateHz, int outputRateHz) {
        if (inputRateHz <= 0 || outputRateHz <= 0) {
            throw new IllegalArgumentException("Sample rates must be positive");
        }
        int samples = pcm16.length / 2;
        if (samples == 0) {
            return EMPTY;
        }
        if (inputRateHz == outputRateHz) {
            lastInputRateHz = inputRateHz;
            lastOutputRateHz = outputRateHz;
            phaseUnits = 0L;
            int evenBytes = samples * 2;
            return evenBytes == pcm16.length ? pcm16 : Arrays.copyOf(pcm16, evenBytes);
        }

        if (inputRateHz != lastInputRateHz || outputRateHz != lastOutputRateHz) {
            configureRates(inputRateHz, outputRateHz);
        }

        long cursor = phaseUnits;
        long end = (long) samples * phaseUnitsPerSample;
        if (cursor >= end) {
            return EMPTY;
        }

        int outSamples = (int) ((end - cursor + phaseStep - 1L) / phaseStep);
        byte[] out = new byte[outSamples * 2];
        int outOffset = 0;

        if (inputRateHz > outputRateHz) {
            int windowSize = Math.max(1, (int) Math.round((double) inputRateHz / outputRateHz));
            while (cursor < end) {
                int startIdx = (int) (cursor / phaseUnitsPerSample);
                int stopIdx = Math.min(samples - 1, startIdx + windowSize - 1);
                long sum = 0L;
                int count = 0;
                for (int i = startIdx; i <= stopIdx; i++) {
                    sum += sampleAt(pcm16, i);
                    count++;
                }
                int averaged = (int) Math.round((double) sum / count);
                outOffset = writeLittleEndian(out, outOffset, clamp(averaged));
                cursor += phaseStep;
            }
        } else {
            while (cursor < end) {
                int leftIndex = (int) (cursor / phaseUnitsPerSample);
                double fraction = (cursor % phaseUnitsPerSample) / (double) phaseUnitsPerSample;
                int left = sampleAt(pcm16, leftIndex);
                int right = leftIndex + 1 < samples ? sampleAt(pcm16, leftIndex + 1) : left;
                int interpolated = (int) Math.round(left + (right - left) * fraction);
                outOffset = writeLittleEndian(out, outOffset, clamp(interpolated));
                cursor += phaseStep;
            }
        }

        phaseUnits = cursor - end;
        return out;
    }

    /** 回到初始相位并清除速率缓存。切换输入源或重启通话时调用。 */
    void reset() {
        phaseUnits = 0L;
        lastInputRateHz = 0;
        lastOutputRateHz = 0;
        phaseUnitsPerSample = 0L;
        phaseStep = 0L;
    }

    private void configureRates(int inputRateHz, int outputRateHz) {
        int divisor = gcd(inputRateHz, outputRateHz);
        lastInputRateHz = inputRateHz;
        lastOutputRateHz = outputRateHz;
        phaseUnitsPerSample = outputRateHz / (long) divisor;
        phaseStep = inputRateHz / (long) divisor;
        phaseUnits = 0L;
    }

    private static int gcd(int a, int b) {
        while (b != 0) {
            int t = a % b;
            a = b;
            b = t;
        }
        return Math.max(1, a);
    }

    private static int sampleAt(byte[] pcm16, int index) {
        int offset = index * 2;
        return (short) ((pcm16[offset] & 0xFF) | (pcm16[offset + 1] << 8));
    }

    private static int clamp(int value) {
        return Math.max(MIN_SAMPLE, Math.min(MAX_SAMPLE, value));
    }

    private static int writeLittleEndian(byte[] out, int offset, int value) {
        out[offset] = (byte) (value & 0xFF);
        out[offset + 1] = (byte) ((value >> 8) & 0xFF);
        return offset + 2;
    }
}
