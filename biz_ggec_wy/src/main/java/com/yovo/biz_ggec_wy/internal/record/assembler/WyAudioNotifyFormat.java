package com.yovo.biz_ggec_wy.internal.record.assembler;

/**
 * WY 音频通道数据切片下行线格式枚举。
 */
public enum WyAudioNotifyFormat {

    WY_QUAD_40_176(
            WyAudioFrameContract.OPUS_BYTES_16K,
            WyAudioFrameContract.FRAMES_PER_NOTIFY_LOCAL,
            WyAudioFrameContract.HEADER_BYTES,
            WyAudioFrameContract.NOTIFY_QUAD_176),

    WY_DUAL_HALF_168(
            WyAudioFrameContract.OPUS_BYTES_32K,
            WyAudioFrameContract.FRAMES_PER_NOTIFY_STREAM,
            WyAudioFrameContract.HEADER_BYTES,
            WyAudioFrameContract.NOTIFY_DUAL_168),

    WY_FULL_FRAME(
            WyAudioFrameContract.OPUS_BYTES_64K,
            WyAudioFrameContract.FRAMES_PER_NOTIFY_STREAM,
            WyAudioFrameContract.HEADER_BYTES,
            WyAudioFrameContract.NOTIFY_BURST_328),

    WY_MTU_SPLIT(
            WyAudioFrameContract.OPUS_BYTES_64K,
            WyAudioFrameContract.FRAMES_PER_NOTIFY_STREAM,
            WyAudioFrameContract.HEADER_BYTES,
            WyAudioFrameContract.NOTIFY_BURST_328),

    STANDARD_LEGACY(
            WyAudioFrameContract.OPUS_BYTES_64K,
            WyAudioFrameContract.FRAMES_PER_NOTIFY_STREAM,
            WyAudioFrameContract.HEADER_LEGACY_BYTES,
            WyAudioFrameContract.NOTIFY_LEGACY_DUAL_336);

    private final int encodedOpusBytes;
    private final int framesPerNotify;
    private final int headerBytes;
    private final int notifyBytes;

    WyAudioNotifyFormat(int encodedOpusBytes, int framesPerNotify,
                        int headerBytes, int notifyBytes) {
        this.encodedOpusBytes = encodedOpusBytes;
        this.framesPerNotify = framesPerNotify;
        this.headerBytes = headerBytes;
        this.notifyBytes = notifyBytes;
    }

    public int getEncodedOpusBytes() {
        return encodedOpusBytes;
    }

    public int getFramesPerNotify() {
        return framesPerNotify;
    }

    public int getHeaderBytes() {
        return headerBytes;
    }

    public int getNotifyBytes() {
        return notifyBytes;
    }

    public int getSubframeBytes() {
        return headerBytes + encodedOpusBytes;
    }
}
