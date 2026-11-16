package com.bes.opus;

import com.yovo.lib_ggec_opus.internal.codec.YoOpusAudioContract;

/**
 * Opus 音频解码器。
 * <p>
 * 入长等于 {@code packet.length}，不按码率推包长、不扫尾零。
 * PCM 输出固定为 16 kHz / 单声道 / 20 ms（640 字节）。
 */
public class OpusDecoder {

    private String TAG = "OpusDecoder";

    /** 兼容旧的 init() + new OpusDecoder() 调用方式，句柄只在线程内短暂传递。 */
    private static final ThreadLocal<Long> PENDING_DECODER = new ThreadLocal<>();

    /** 解码器实例指针；不得跨实例共享，否则旧会话销毁会破坏新会话。 */
    private long decoder;

    static {
        System.loadLibrary("besOpus");
    }

    public OpusDecoder() {
        Long pending = PENDING_DECODER.get();
        PENDING_DECODER.remove();
        decoder = pending == null ? 0L : pending;
    }

    private static native long init_native(int framePeriod, int singleFrameInputSize,
                                           int totalFrameInputSize, int pcmOutputSize, int bitRate);

    private static native int getOpusSingleFrameDecoderIntLen_native();

    private static native int getOpusFrameTotalFrameDecoderIntLen_native();

    private native short[] decode_native(long decoder, byte[] decodeDataIn, int encodedLength);

    private native void destory_native(long decoder);

    private native void setBitRate_native(long decoder, int bitRate);

    /**
     * 建立 16 kHz / 1ch / PCM 640 的解码器。包长由每次 {@link #decode(byte[])} 的数组长度决定。
     */
    public static void init() {
        PENDING_DECODER.set(init_native(
                -1, 0, 0, YoOpusAudioContract.PCM_FRAME_BYTES, 0));
    }

    /**
     * @deprecated 码率不再决定入包长度，请用 {@link #init()}。
     */
    @Deprecated
    public static void init(int framePeriod, int singleFrameInputSize, int totalFrameInputSize,
                            int pcmOutputSize, int bitRate) {
        int pcm = pcmOutputSize > 0 ? pcmOutputSize : YoOpusAudioContract.PCM_FRAME_BYTES;
        PENDING_DECODER.set(init_native(framePeriod, 0, 0, pcm, bitRate));
    }

    /**
     * @deprecated 包长不再由 native 全局配置给出。
     */
    @Deprecated
    public static int getOpusSingleFrameDecoderIntLen() {
        return getOpusSingleFrameDecoderIntLen_native();
    }

    /**
     * @deprecated 包长不再由 native 全局配置给出。
     */
    @Deprecated
    public static int getOpusTotalFrameDecoderIntLen() {
        return getOpusFrameTotalFrameDecoderIntLen_native();
    }

    /**
     * 解码一包 Opus 为 PCM 字节数组。入长 = {@code packet.length}。
     *
     * @return 解码后的 PCM；失败返回 null
     */
    public byte[] decode(byte[] packet) {
        if (decoder == 0) {
            decoder = init_native(-1, 0, 0, YoOpusAudioContract.PCM_FRAME_BYTES, 0);
        }
        int inLen = YoOpusAudioContract.decodeInputLength(packet);
        if (inLen == 0) {
            return null;
        }
        short[] shorts = decode_native(decoder, packet, inLen);
        if (shorts != null) {
            return OpusUtils.Shorts2Bytes(shorts);
        }
        return null;
    }

    /**
     * 将整段缓冲区视为一包解码（不再按固定槽位切分）。
     */
    public byte[] decodeAll(byte[] totalDataIn) {
        return decode(totalDataIn);
    }

    /**
     * 保留 CTL 兼容；解码切包不依赖码率。
     */
    public void setBitRate(int bitRate) {
        if (bitRate <= 0 || decoder == 0L) {
            return;
        }
        setBitRate_native(decoder, bitRate);
    }

    /**
     * 销毁解码器。方法名保持 destory 以维持 JNI / 旧调用兼容。
     */
    public synchronized void descory() {
        long handle = decoder;
        decoder = 0L;
        if (handle != 0L) {
            destory_native(handle);
        }
    }
}
