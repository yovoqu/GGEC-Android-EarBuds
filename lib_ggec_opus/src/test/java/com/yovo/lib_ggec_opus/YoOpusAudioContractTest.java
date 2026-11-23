package com.yovo.lib_ggec_opus;

import static org.junit.Assert.assertEquals;

import com.yovo.lib_ggec_opus.api.YoOpusAudioContract;

import org.junit.Test;

/**
 * 音频几何/码率契约：码率必须由「字节数 × 8 × 1000 / 帧周期(ms)」推导，
 * 并与设备端录音参数（16 kHz / 16 bit / 单声道 / 20 ms）严格自洽。
 *
 * <p>关键不变量：40B/20ms = 16 kbps；640B/20ms = 256 kbps；压缩比 = 16:1。</p>
 */
public final class YoOpusAudioContractTest {

    @Test
    public void opus_40bytes_per20ms_is_16000bps() {
        assertEquals(16_000, YoOpusAudioContract.OPUS_BIT_RATE_16K);
        assertEquals("40B × 8 × 1000 / 20 必须等于 16000",
                16_000, YoOpusAudioContract.OPUS_BYTES_16K * 8 * 1000
                        / YoOpusAudioContract.FRAME_PERIOD_MS);
    }

    @Test
    public void pcm_640bytes_per20ms_is_256000bps() {
        assertEquals(256_000, YoOpusAudioContract.PCM_BIT_RATE);
        assertEquals("640B × 8 × 1000 / 20 必须等于 256000",
                256_000, YoOpusAudioContract.PCM_FRAME_BYTES * 8 * 1000
                        / YoOpusAudioContract.FRAME_PERIOD_MS);
    }

    @Test
    public void compressionRatio_is_16_to_1() {
        assertEquals(16, YoOpusAudioContract.COMPRESSION_RATIO);
        assertEquals("压缩比 = PCM 码率 / wire Opus 码率",
                YoOpusAudioContract.PCM_BIT_RATE / YoOpusAudioContract.WIRE_OPUS_BIT_RATE,
                YoOpusAudioContract.COMPRESSION_RATIO);
    }

    @Test
    public void wireBitRate_is_16k_and_distinct_from_legacyDefault() {
        assertEquals("wire Opus 码率取 16 kbps", 16_000, YoOpusAudioContract.WIRE_OPUS_BIT_RATE);
        assertEquals("既有 BIT_RATE_DEFAULT 必须保持 32000 不变（其它代码依赖）",
                32_000, YoOpusAudioContract.BIT_RATE_DEFAULT);
        assertEquals(16_000, YoOpusAudioContract.BIT_RATE_MIN);
        assertEquals(64_000, YoOpusAudioContract.BIT_RATE_MAX);
    }

    @Test
    public void higherBitRateConstants_matchFrameGeometry() {
        assertEquals(32_000, YoOpusAudioContract.OPUS_BIT_RATE_32K);
        assertEquals(64_000, YoOpusAudioContract.OPUS_BIT_RATE_64K);
    }
}
