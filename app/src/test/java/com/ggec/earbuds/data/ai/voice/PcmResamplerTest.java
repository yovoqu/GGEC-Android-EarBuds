package com.ggec.earbuds.data.ai.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 16k → 24k 的升采样。
 *
 * <p>这里断言的都是"字节数怎么变、边界会不会炸"，不断言波形细节：相位与插值的正确性
 * 由 1.5 倍这个整数关系间接保证——比例错了，累积误差一定会在多帧之后露出来。</p>
 */
public class PcmResamplerTest {

    @Test
    public void oneFrameBecomesExactlyOneAndAHalfTimesLonger() {
        PcmResampler resampler = new PcmResampler();

        byte[] out = resampler.resample(new byte[640]);

        // 640 字节 = 320 个采样，320 × 1.5 = 480 个采样 = 960 字节。
        assertEquals(960, out.length);
    }

    @Test
    public void ratioStaysExactAcrossManyFrames() {
        PcmResampler resampler = new PcmResampler();
        int total = 0;

        // 连续 50 帧：如果实现里相位用浮点累加，误差会在这里攒出来。
        for (int frame = 0; frame < 50; frame++) {
            total += resampler.resample(new byte[640]).length;
        }

        assertEquals(50 * 640 * 3 / 2, total);
    }

    @Test
    public void oddByteCountDropsTheHalfSample() {
        PcmResampler resampler = new PcmResampler();

        // 401 字节：最后半个采样（1 字节）必须被丢掉，否则之后所有采样都错位半格。
        byte[] out = resampler.resample(new byte[401]);

        assertEquals("输出永远是整数个采样", 0, out.length % 2);
        assertTrue("400 字节输入应该产生 600 字节", out.length == 600);
    }

    @Test
    public void emptyInputStaysEmpty() {
        PcmResampler resampler = new PcmResampler();

        assertEquals(0, resampler.resample(new byte[0]).length);
        assertEquals(0, resampler.resample(new byte[1]).length);
    }

    @Test
    public void silenceProducesSilenceWithoutBlowingUp() {
        PcmResampler resampler = new PcmResampler();

        byte[] out = resampler.resample(new byte[640]);

        for (byte value : out) {
            assertEquals("静音升采样之后还得是静音", 0, value);
        }
    }

    @Test
    public void fullScaleInputDoesNotWrapAround() {
        PcmResampler resampler = new PcmResampler();
        byte[] loud = new byte[640];
        for (int i = 0; i + 1 < loud.length; i += 2) {
            loud[i] = (byte) 0xFF;      // 小端 0x7FFF = 32767
            loud[i + 1] = 0x7F;
        }

        byte[] out = resampler.resample(loud);

        // 插值算的是 int，若忘了夹回 short 范围，正的最大值会翻成负的最小值（听得见的爆音）。
        for (int i = 0; i + 1 < out.length; i += 2) {
            int sample = (short) ((out[i] & 0xFF) | (out[i + 1] << 8));
            assertEquals(32767, sample);
        }
    }

    @Test
    public void shortestPossibleFrameDoesNotHang() {
        PcmResampler resampler = new PcmResampler();

        byte[] out = resampler.resample(new byte[]{0x10, 0x00});

        assertEquals(0, out.length % 2);
        assertTrue("一个采样也该有输出", out.length >= 2);
    }
}
