package com.ggec.earbuds.data.ai.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 通用有理数比值 {@link PcmResampler} 单测：覆盖升采样、降采样、同速率直通与热切换自适应。
 */
public class PcmResamplerTest {

    @Test
    public void upsampling16kTo24kProducesExactOneAndAHalfRatioWithoutDrift() {
        PcmResampler resampler = new PcmResampler();
        int total = 0;

        for (int frame = 0; frame < 50; frame++) {
            total += resampler.resample(new byte[640], 16_000, 24_000).length;
        }

        assertEquals(50 * 640 * 3 / 2, total);
    }

    @Test
    public void downsampling48kTo24kAnd16kProduceExactRatiosAndAntiAlias() {
        PcmResampler resampler = new PcmResampler();

        byte[] in48k = constantSignal(960 * 2, (short) 12_000);
        byte[] out24k = resampler.resample(in48k, 48_000, 24_000);
        assertEquals(960, out24k.length);
        assertAllSamplesEqual(out24k, (short) 12_000);

        byte[] out16k = resampler.resample(in48k, 48_000, 16_000);
        assertEquals(640, out16k.length);
        assertAllSamplesEqual(out16k, (short) 12_000);
    }

    @Test
    public void upsampling8kTo16kDoublesLengthCleanly() {
        PcmResampler resampler = new PcmResampler();

        byte[] out = resampler.resample(new byte[320], 8_000, 16_000);
        assertEquals(640, out.length);
    }

    @Test
    public void sameRateEvenBytesBypassWithZeroCopy() {
        PcmResampler resampler = new PcmResampler();
        byte[] frame = new byte[640];

        assertSame(frame, resampler.resample(frame, 16_000, 16_000));
        assertSame(frame, resampler.resample(frame, 24_000, 24_000));
        assertEquals(640, resampler.resample(new byte[641], 24_000, 24_000).length);
    }

    @Test
    public void switchingInputRatesMidStreamResetsPhaseCleanly() {
        PcmResampler resampler = new PcmResampler();

        assertEquals(960, resampler.resample(new byte[640], 16_000, 24_000).length);
        assertEquals(960, resampler.resample(new byte[1920], 48_000, 24_000).length);
        assertEquals(960, resampler.resample(new byte[320], 8_000, 24_000).length);
    }

    @Test
    public void oddByteCountAndBoundariesStaySafe() {
        PcmResampler resampler = new PcmResampler();

        byte[] out = resampler.resample(new byte[401], 16_000, 24_000);
        assertEquals(0, out.length % 2);
        assertEquals(600, out.length);

        assertEquals(0, resampler.resample(new byte[0], 16_000, 24_000).length);
        assertEquals(0, resampler.resample(new byte[1], 16_000, 24_000).length);

        byte[] loud = constantSignal(640, Short.MAX_VALUE);
        assertAllSamplesEqual(resampler.resample(loud, 16_000, 24_000), Short.MAX_VALUE);

        byte[] tiny = resampler.resample(new byte[]{0x10, 0x00}, 16_000, 24_000);
        assertEquals(0, tiny.length % 2);
        assertTrue(tiny.length >= 2);
    }

    private static byte[] constantSignal(int byteCount, short value) {
        byte[] data = new byte[byteCount];
        for (int i = 0; i + 1 < data.length; i += 2) {
            data[i] = (byte) (value & 0xFF);
            data[i + 1] = (byte) ((value >> 8) & 0xFF);
        }
        return data;
    }

    private static void assertAllSamplesEqual(byte[] pcm16, short expected) {
        for (int i = 0; i + 1 < pcm16.length; i += 2) {
            short sample = (short) ((pcm16[i] & 0xFF) | (pcm16[i + 1] << 8));
            assertEquals(expected, sample);
        }
    }
}
