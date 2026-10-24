package com.yovo.lib_ai.speech;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

/**
 * 把裸 PCM 采样包成最小的 wav（RIFF/WAVE，PCM 格式 1）容器。
 *
 * <p>为什么需要它：采集侧（{@code AudioRecord}）给出的就是一串小端 16bit 采样，
 * 没有任何文件头；而 Whisper 兼容的 {@code /audio/transcriptions} 要求带上可识别的
 * 音频容器，否则服务端只能按 {@code Content-Type} 猜。这里只写 44 字节的标准头，
 * 不写 LIST/INFO 之类的扩展块——转写服务不读它们，多写的字节只是上传负担。
 *
 * <p>头部必须写全 {@code RIFF} 和 {@code data} 两处长度：只写其中一处是实际踩过的坑，
 * 部分网关会据此认为上传被截断并直接 400。
 */
public final class WavEncoder {

    /** 采集与转写统一使用的采样率。再高对语音识别没有收益，只增加上传量。 */
    public static final int SAMPLE_RATE_HZ = 16000;

    /** 单声道：耳机麦克风只有一路，双声道等于把同一路复制两遍。 */
    public static final int CHANNELS = 1;

    /** 固定 16bit：与 {@code AudioRecord} 的 ENCODING_PCM_16BIT 对齐，不做重采样。 */
    public static final int BITS_PER_SAMPLE = 16;

    /** RIFF(12) + fmt 块(8 + 16) + data 块头(8)。 */
    public static final int HEADER_BYTES = 44;

    private static final int PCM_FORMAT = 1;

    private WavEncoder() {
    }

    /**
     * 按采集约定（16kHz / 单声道 / 16bit）封装。
     *
     * @param pcm16 小端 16bit 采样字节，长度必须是 2 的整数倍
     */
    @NonNull
    public static byte[] encode(@NonNull byte[] pcm16) {
        return encode(pcm16, SAMPLE_RATE_HZ, CHANNELS);
    }

    /**
     * 按指定采样率与声道数封装，供将来接入 48kHz 或立体声采集时复用。
     *
     * <p>空输入不视为错误：返回只有头部的 44 字节 wav。空录音是否要发出去由调用方决定，
     * 编码器不做业务判断，也不伪造采样。
     *
     * @throws IllegalArgumentException 采样率/声道数非正，或字节数与 16bit 帧长对不齐
     */
    @NonNull
    public static byte[] encode(@NonNull byte[] pcm16, int sampleRateHz, int channels) {
        Objects.requireNonNull(pcm16, "pcm16");
        if (sampleRateHz <= 0) {
            throw new IllegalArgumentException("sampleRateHz must be > 0");
        }
        if (channels <= 0) {
            throw new IllegalArgumentException("channels must be > 0");
        }
        int bytesPerFrame = channels * (BITS_PER_SAMPLE / 8);
        if (pcm16.length % bytesPerFrame != 0) {
            throw new IllegalArgumentException(
                    "pcm16 length must be a multiple of " + bytesPerFrame + " bytes per frame");
        }
        if (pcm16.length > Integer.MAX_VALUE - HEADER_BYTES) {
            throw new IllegalArgumentException("pcm16 is too large to wrap");
        }

        int dataBytes = pcm16.length;
        int blockAlign = bytesPerFrame;
        int byteRate = sampleRateHz * blockAlign;

        ByteBuffer buffer = ByteBuffer.allocate(HEADER_BYTES + dataBytes).order(ByteOrder.LITTLE_ENDIAN);
        putAscii(buffer, "RIFF");
        buffer.putInt(36 + dataBytes);          // RIFF 块长度 = 文件长度 - 8
        putAscii(buffer, "WAVE");
        putAscii(buffer, "fmt ");
        buffer.putInt(16);                      // PCM 的 fmt 块固定 16 字节
        buffer.putShort((short) PCM_FORMAT);
        buffer.putShort((short) channels);
        buffer.putInt(sampleRateHz);
        buffer.putInt(byteRate);
        buffer.putShort((short) blockAlign);
        buffer.putShort((short) BITS_PER_SAMPLE);
        putAscii(buffer, "data");
        buffer.putInt(dataBytes);
        buffer.put(pcm16);
        return buffer.array();
    }

    private static void putAscii(ByteBuffer buffer, String value) {
        for (int i = 0; i < value.length(); i++) {
            buffer.put((byte) value.charAt(i));
        }
    }
}
