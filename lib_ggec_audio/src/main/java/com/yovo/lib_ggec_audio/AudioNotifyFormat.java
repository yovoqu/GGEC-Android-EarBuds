package com.yovo.lib_ggec_audio;

/**
 * 音频通道下行线的 Notify 线格式。
 *
 * <p>同一族固件会在运行期切换线格式，且一次会话内不再变：装配器按首个可判定窗口锁定一种，
 * 锁定后整会话沿用。</p>
 *
 * <p><b>布局是几何的一部分，不是实现细节</b>：</p>
 * <ul>
 *   <li>{@link FrameLayout#PER_FRAME_HEADER}：每帧各带一个序号头，帧步长 = 头长 + Opus 长；</li>
 *   <li>{@link FrameLayout#NOTIFY_HEADER}：整包只带一个通知头，帧步长 = Opus 长，
 *       包内后续帧的序号是隐式的（首帧序号 + 帧内偏移）。</li>
 * </ul>
 * <p>把后者当成前者切帧，会把「一包 4 帧」误读成「一帧 160B」，直接产出 Opus
 * {@code corrupted stream}。</p>
 */
public enum AudioNotifyFormat {

    /** 176 字节一包 4 帧：每帧 4B 头 + 40B Opus（16 kbps）。 */
    QUAD_40_176(
            FrameLayout.PER_FRAME_HEADER,
            AudioFrameContract.OPUS_BYTES_16K,
            AudioFrameContract.FRAMES_PER_NOTIFY_LOCAL,
            AudioFrameContract.HEADER_BYTES,
            AudioFrameContract.NOTIFY_QUAD_176),

    /** 440 字节一包 10 帧：每帧 4B 头 + 40B Opus（16 kbps）。 */
    DECA_40_440(
            FrameLayout.PER_FRAME_HEADER,
            AudioFrameContract.OPUS_BYTES_16K,
            AudioFrameContract.FRAMES_PER_BURST_10,
            AudioFrameContract.HEADER_BYTES,
            AudioFrameContract.NOTIFY_BURST_440),

    /**
     * 168 字节一包 4 帧：**整包一个** 8B 通知头 + 4 个连续 40B Opus（16 kbps）。
     *
     * <p>现场判据：相邻 Notify 头部序号差恒为 4（即每包 4 帧），包长恒为 168。</p>
     */
    QUAD_40_168(
            FrameLayout.NOTIFY_HEADER,
            AudioFrameContract.OPUS_BYTES_16K,
            AudioFrameContract.FRAMES_PER_NOTIFY_LOCAL,
            AudioFrameContract.HEADER_LEGACY_BYTES,
            AudioFrameContract.NOTIFY_QUAD_40_168),

    /** 168 字节一包 2 帧：每帧 4B 头 + 80B Opus（32 kbps）。 */
    DUAL_HALF_168(
            FrameLayout.PER_FRAME_HEADER,
            AudioFrameContract.OPUS_BYTES_32K,
            AudioFrameContract.FRAMES_PER_NOTIFY_STREAM,
            AudioFrameContract.HEADER_BYTES,
            AudioFrameContract.NOTIFY_DUAL_168),

    /** 单帧整包：4B 头 + 160B Opus（64 kbps），一包最多两帧。 */
    FULL_FRAME(
            FrameLayout.PER_FRAME_HEADER,
            AudioFrameContract.OPUS_BYTES_64K,
            AudioFrameContract.FRAMES_PER_NOTIFY_STREAM,
            AudioFrameContract.HEADER_BYTES,
            AudioFrameContract.NOTIFY_BURST_328),

    /** MTU 拆分：首包 164B 帧 + 4B 下一帧头，后续包 160B Opus + 8B 残段。 */
    MTU_SPLIT(
            FrameLayout.PER_FRAME_HEADER,
            AudioFrameContract.OPUS_BYTES_64K,
            AudioFrameContract.FRAMES_PER_NOTIFY_STREAM,
            AudioFrameContract.HEADER_BYTES,
            AudioFrameContract.NOTIFY_BURST_328),

    /** 旧线格式：8B 头 + 160B Opus。 */
    STANDARD_LEGACY(
            FrameLayout.PER_FRAME_HEADER,
            AudioFrameContract.OPUS_BYTES_64K,
            AudioFrameContract.FRAMES_PER_NOTIFY_STREAM,
            AudioFrameContract.HEADER_LEGACY_BYTES,
            AudioFrameContract.NOTIFY_LEGACY_DUAL_336);

    /** 序号头落在哪一级：每帧一个，还是整包一个。 */
    public enum FrameLayout {
        PER_FRAME_HEADER,
        NOTIFY_HEADER
    }

    private final FrameLayout layout;
    private final int encodedOpusBytes;
    private final int framesPerNotify;
    private final int headerBytes;
    private final int notifyBytes;

    AudioNotifyFormat(FrameLayout layout,
                      int encodedOpusBytes,
                      int framesPerNotify,
                      int headerBytes,
                      int notifyBytes) {
        this.layout = layout;
        this.encodedOpusBytes = encodedOpusBytes;
        this.framesPerNotify = framesPerNotify;
        this.headerBytes = headerBytes;
        this.notifyBytes = notifyBytes;
    }

    public FrameLayout getLayout() {
        return layout;
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

    /** 头 + 单帧 Opus：仅在 {@link FrameLayout#PER_FRAME_HEADER} 下等于帧步长。 */
    public int getSubframeBytes() {
        return headerBytes + encodedOpusBytes;
    }

    /** 相邻两帧在字节流里的步长（与布局无关的正确切帧依据）。 */
    public int getFrameStrideBytes() {
        return layout == FrameLayout.NOTIFY_HEADER ? encodedOpusBytes : getSubframeBytes();
    }

    /** 包内第 {@code index} 帧的 Opus 起始偏移（相对包首）。 */
    public int payloadOffsetOf(int index) {
        return layout == FrameLayout.NOTIFY_HEADER
                ? headerBytes + index * encodedOpusBytes
                : index * getSubframeBytes() + headerBytes;
    }

    /** 本格式一个 Notify 实际承载的帧数（由几何推导，不信任字面量）。 */
    public int framesCoveredByNotify() {
        return layout == FrameLayout.NOTIFY_HEADER
                ? framesPerNotify
                : notifyBytes / Math.max(1, getSubframeBytes());
    }
}
