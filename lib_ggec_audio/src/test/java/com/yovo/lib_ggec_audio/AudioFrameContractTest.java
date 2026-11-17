package com.yovo.lib_ggec_audio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_ggec_opus.api.YoOpusAudioContract;

import org.junit.Test;

/**
 * 帧几何单源：AudioFrameContract 的采样/帧长/Opus 单帧字节数必须来自
 * lib_ggec_opus 的 api 契约，本模块不再自带重复字面量。
 */
public final class AudioFrameContractTest {

    @Test
    public void geometryComesFromOpusApiContract() {
        assertEquals(YoOpusAudioContract.SAMPLE_RATE, AudioFrameContract.SAMPLE_RATE);
        assertEquals(YoOpusAudioContract.CHANNEL_COUNT, AudioFrameContract.CHANNEL_COUNT);
        assertEquals(YoOpusAudioContract.FRAME_PERIOD_MS, AudioFrameContract.FRAME_PERIOD_MS);
        assertEquals(YoOpusAudioContract.PCM_SAMPLES_PER_FRAME,
                AudioFrameContract.PCM_SAMPLES_PER_FRAME);
        assertEquals(YoOpusAudioContract.PCM_FRAME_BYTES, AudioFrameContract.PCM_FRAME_BYTES);
        assertEquals(YoOpusAudioContract.OPUS_BYTES_16K, AudioFrameContract.OPUS_BYTES_16K);
        assertEquals(YoOpusAudioContract.OPUS_BYTES_32K, AudioFrameContract.OPUS_BYTES_32K);
        assertEquals(YoOpusAudioContract.OPUS_BYTES_64K, AudioFrameContract.OPUS_BYTES_64K);
        assertEquals(YoOpusAudioContract.BIT_RATE_DEFAULT, AudioFrameContract.BIT_RATE_DEFAULT);
    }

    @Test
    public void wireFormatIsDerivedNotHardcoded() {
        assertEquals(AudioFrameContract.HEADER_BYTES + AudioFrameContract.OPUS_BYTES_16K,
                AudioFrameContract.SUBFRAME_44);
        assertEquals(AudioFrameContract.SUBFRAME_44 * AudioFrameContract.FRAMES_PER_NOTIFY_LOCAL,
                AudioFrameContract.NOTIFY_QUAD_176);
        assertEquals(AudioFrameContract.SUBFRAME_44 * AudioFrameContract.FRAMES_PER_BURST_10,
                AudioFrameContract.NOTIFY_BURST_440);
        assertEquals(AudioFrameContract.SUBFRAME_84 * AudioFrameContract.FRAMES_PER_NOTIFY_STREAM,
                AudioFrameContract.NOTIFY_DUAL_168);
        assertEquals(AudioFrameContract.NOTIFY_DUAL_168, AudioFrameContract.NOTIFY_MTU_FIRST);
        assertEquals(AudioFrameContract.PCM_SAMPLES_PER_FRAME * 2,
                AudioFrameContract.PCM_FRAME_BYTES);
        assertTrue("16k 单帧必须小于 32k 单帧",
                AudioFrameContract.OPUS_BYTES_16K < AudioFrameContract.OPUS_BYTES_32K);
    }
}
