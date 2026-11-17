package com.yovo.lib_ggec_audio;

/**
 * 音频通道下行线的 Notify 线格式。
 *
 * <p>同一族固件会在运行期切换线格式，且一次会话内不再变：装配器按首包锁定一种，锁定后整会话沿用。</p>
 */
public enum AudioNotifyFormat {

    /** 176 字节一包 4 帧：每帧 4B 头 + 40B Opus（16 kbps）。 */
    QUAD_40_176(
            AudioFrameContract.OPUS_BYTES_16K,
            AudioFrameContract.FRAMES_PER_NOTIFY_LOCAL,
            AudioFrameContract.HEADER_BYTES,
            AudioFrameContract.NOTIFY_QUAD_176),

    /** 168 字节一包 2 帧：每帧 4B 头 + 80B Opus（32 kbps）。 */
    DUAL_HALF_168(
            AudioFrameContract.OPUS_BYTES_32K,
            AudioFrameContract.FRAMES_PER_NOTIFY_STREAM,
            AudioFrameContract.HEADER_BYTES,
            AudioFrameContract.NOTIFY_DUAL_168),

    /** 单帧整包：4B 头 + 160B Opus（64 kbps），一包最多两帧。 */
    FULL_FRAME(
            AudioFrameContract.OPUS_BYTES_64K,
            AudioFrameContract.FRAMES_PER_NOTIFY_STREAM,
            AudioFrameContract.HEADER_BYTES,
            AudioFrameContract.NOTIFY_BURST_328),

    /** MTU 拆分：首包 164B 帧 + 4B 下一帧头，后续包 160B Opus + 8B 残段。 */
    MTU_SPLIT(
            AudioFrameContract.OPUS_BYTES_64K,
            AudioFrameContract.FRAMES_PER_NOTIFY_STREAM,
            AudioFrameContract.HEADER_BYTES,
            AudioFrameContract.NOTIFY_BURST_328),

    /** 旧线格式：8B 头 + 160B Opus。 */
    STANDARD_LEGACY(
            AudioFrameContract.OPUS_BYTES_64K,
            AudioFrameContract.FRAMES_PER_NOTIFY_STREAM,
            AudioFrameContract.HEADER_LEGACY_BYTES,
            AudioFrameContract.NOTIFY_LEGACY_DUAL_336);

    private final int encodedOpusBytes;
    private final int framesPerNotify;
    private final int headerBytes;
    private final int notifyBytes;

    AudioNotifyFormat(int encodedOpusBytes, int framesPerNotify,
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
