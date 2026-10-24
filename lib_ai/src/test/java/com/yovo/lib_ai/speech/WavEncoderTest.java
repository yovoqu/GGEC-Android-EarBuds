package com.yovo.lib_ai.speech;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * 只测头部字段与长度：wav 头的错法通常很小（长度少 8 字节、byteRate 按单声道算），
 * 但错一处服务端就整段拒收，所以逐字段断言，而不是只看文件大小。
 */
public final class WavEncoderTest {

    private static final int HEADER = WavEncoder.HEADER_BYTES;

    @Test
    public void writesCanonicalHeaderForCaptureDefaults() {
        byte[] pcm = new byte[3200];                    // 100ms：16000Hz × 2 字节/帧
        for (int i = 0; i < pcm.length; i++) {
            pcm[i] = (byte) i;
        }

        byte[] wav = WavEncoder.encode(pcm);

        assertEquals(HEADER + pcm.length, wav.length);
        assertEquals("RIFF", ascii(wav, 0));
        assertEquals(36 + pcm.length, le32(wav, 4));    // RIFF 块长度 = 文件长度 - 8
        assertEquals("WAVE", ascii(wav, 8));
        assertEquals("fmt ", ascii(wav, 12));
        assertEquals(16, le32(wav, 16));                // PCM 的 fmt 块固定 16 字节
        assertEquals(1, le16(wav, 20));                 // 格式 1 = PCM
        assertEquals(1, le16(wav, 22));                 // 单声道
        assertEquals(16000, le32(wav, 24));             // 采样率
        assertEquals(32000, le32(wav, 28));             // byteRate = 16000 × 1 × 2
        assertEquals(2, le16(wav, 32));                 // blockAlign
        assertEquals(16, le16(wav, 34));                // 位深
        assertEquals("data", ascii(wav, 36));
        assertEquals(pcm.length, le32(wav, 40));        // data 块长度

        byte[] payload = new byte[pcm.length];
        System.arraycopy(wav, HEADER, payload, 0, pcm.length);
        assertArrayEquals(pcm, payload);
    }

    @Test
    public void keepsHeaderConsistentForEmptyInput() {
        byte[] wav = WavEncoder.encode(new byte[0]);

        assertEquals(HEADER, wav.length);
        assertEquals("RIFF", ascii(wav, 0));
        assertEquals(36, le32(wav, 4));
        assertEquals("data", ascii(wav, 36));
        assertEquals(0, le32(wav, 40));
    }

    @Test
    public void scalesByteRateAndBlockAlignWithFormat() {
        byte[] wav = WavEncoder.encode(new byte[4], 48000, 2);

        assertEquals(48000, le32(wav, 24));
        assertEquals(192000, le32(wav, 28));            // 48000 × 2 声道 × 2 字节
        assertEquals(2, le16(wav, 22));
        assertEquals(4, le16(wav, 32));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsHalfFrame() {
        // 16bit 单声道一帧 2 字节；奇数长度说明采集侧传错了位宽。
        WavEncoder.encode(new byte[3]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonPositiveSampleRate() {
        WavEncoder.encode(new byte[2], 0, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonPositiveChannels() {
        WavEncoder.encode(new byte[2], 16000, 0);
    }

    private static String ascii(byte[] source, int offset) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            builder.append((char) (source[offset + i] & 0xff));
        }
        return builder.toString();
    }

    private static int le32(byte[] source, int offset) {
        return (source[offset] & 0xff)
                | (source[offset + 1] & 0xff) << 8
                | (source[offset + 2] & 0xff) << 16
                | (source[offset + 3] & 0xff) << 24;
    }

    private static int le16(byte[] source, int offset) {
        return (source[offset] & 0xff) | (source[offset + 1] & 0xff) << 8;
    }
}
