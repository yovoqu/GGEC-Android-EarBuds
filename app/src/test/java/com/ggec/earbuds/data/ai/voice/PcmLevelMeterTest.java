package com.ggec.earbuds.data.ai.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.testing.ai.FakeAudioSource;

import org.junit.Test;

/**
 * {@link PcmLevelMeter} dBFS 对数归一化单测。
 */
public class PcmLevelMeterTest {

    @Test
    public void nullEmptyAndDigitalSilenceReturnZero() {
        assertEquals(0f, PcmLevelMeter.levelOf(null), 0.0001f);
        assertEquals(0f, PcmLevelMeter.levelOf(new byte[0]), 0.0001f);
        assertEquals(0f, PcmLevelMeter.levelOf(new byte[]{1}), 0.0001f);
        assertEquals(0f, PcmLevelMeter.levelOf(new byte[640]), 0.0001f);
    }

    @Test
    public void audibleSignalsScaleMonotonicallyInRange() {
        float quiet = PcmLevelMeter.levelOf(FakeAudioSource.pcmTone(640, (short) 20));
        float normal = PcmLevelMeter.levelOf(FakeAudioSource.pcmTone(640, (short) 1500));
        float loud = PcmLevelMeter.levelOf(FakeAudioSource.pcmTone(640, Short.MAX_VALUE));

        assertTrue("微弱有声信号至少返回最小可见电平", quiet >= 0.02f);
        assertTrue("响度递增", normal > quiet && loud > normal);
        assertEquals("满幅信号钳位到 1.0", 1.0f, loud, 0.0001f);
    }
}
