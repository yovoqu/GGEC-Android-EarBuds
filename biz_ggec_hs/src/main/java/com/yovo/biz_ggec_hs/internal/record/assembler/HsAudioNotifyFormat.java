package com.yovo.biz_ggec_hs.internal.record.assembler;

/**
 * HS02 音频通道 BLE Notify 的下行线格式。
 */
public enum HsAudioNotifyFormat {

    HS02_QUAD_40_176(
            HsAudioFrameContract.OPUS_BYTES_16K,
            HsAudioFrameContract.FRAMES_PER_NOTIFY_LOCAL,
            HsAudioFrameContract.HEADER_BYTES,
            HsAudioFrameContract.NOTIFY_QUAD_176),

    HS02_DUAL_HALF_168(
            HsAudioFrameContract.OPUS_BYTES_32K,
            HsAudioFrameContract.FRAMES_PER_NOTIFY_STREAM,
            HsAudioFrameContract.HEADER_BYTES,
            HsAudioFrameContract.NOTIFY_DUAL_168),

    HS02_FULL_FRAME(
            HsAudioFrameContract.OPUS_BYTES_64K,
            HsAudioFrameContract.FRAMES_PER_NOTIFY_STREAM,
            HsAudioFrameContract.HEADER_BYTES,
            HsAudioFrameContract.NOTIFY_BURST_328),

    HS02_MTU_SPLIT(
            HsAudioFrameContract.OPUS_BYTES_64K,
            HsAudioFrameContract.FRAMES_PER_NOTIFY_STREAM,
            HsAudioFrameContract.HEADER_BYTES,
            HsAudioFrameContract.NOTIFY_BURST_328),

    STANDARD_LEGACY(
            HsAudioFrameContract.OPUS_BYTES_64K,
            HsAudioFrameContract.FRAMES_PER_NOTIFY_STREAM,
            HsAudioFrameContract.HEADER_LEGACY_BYTES,
            HsAudioFrameContract.NOTIFY_LEGACY_DUAL_336);

    private final int encodedOpusBytes;
    private final int framesPerNotify;
    private final int headerBytes;
    private final int notifyBytes;

    HsAudioNotifyFormat(int encodedOpusBytes, int framesPerNotify,
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
