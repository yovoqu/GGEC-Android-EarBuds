package com.yovo.biz_ggec_hs.internal.record.assembler;

/**
 * HS02 音频帧结构与码率契约常量。
 */
public final class HsAudioFrameContract {

    public static final int SAMPLE_RATE = 16_000;
    public static final int CHANNEL_COUNT = 1;
    public static final int FRAME_PERIOD_MS = 20;
    public static final int PCM_SAMPLES_PER_FRAME = 320;
    public static final int PCM_FRAME_BYTES = 640;

    public static final int HEADER_BYTES = 4;
    public static final int HEADER_LEGACY_BYTES = 8;
    public static final int STARTING_DATA_INDEX = 0;

    public static final int OPUS_BYTES_16K = 40;
    public static final int OPUS_BYTES_32K = 80;
    public static final int OPUS_BYTES_64K = 160;

    public static final int BIT_RATE_DEFAULT = 32_000;
    public static final int BIT_RATE_16K = 16_000;
    public static final int BIT_RATE_32K = 32_000;

    public static final int FRAMES_PER_NOTIFY_LOCAL = 4;
    public static final int FRAMES_PER_NOTIFY_STREAM = 2;

    public static final int SUBFRAME_44 = HEADER_BYTES + OPUS_BYTES_16K;
    public static final int SUBFRAME_84 = HEADER_BYTES + OPUS_BYTES_32K;
    public static final int SUBFRAME_164 = HEADER_BYTES + OPUS_BYTES_64K;
    public static final int PACKET_LEGACY_168 = HEADER_LEGACY_BYTES + OPUS_BYTES_64K;

    public static final int NOTIFY_QUAD_176 = SUBFRAME_44 * FRAMES_PER_NOTIFY_LOCAL;
    public static final int NOTIFY_DUAL_168 = SUBFRAME_84 * FRAMES_PER_NOTIFY_STREAM;
    public static final int NOTIFY_BURST_328 = SUBFRAME_164 * FRAMES_PER_NOTIFY_STREAM;
    public static final int NOTIFY_LEGACY_DUAL_336 = PACKET_LEGACY_168 * FRAMES_PER_NOTIFY_STREAM;
    public static final int NOTIFY_MTU_FIRST = NOTIFY_DUAL_168;

    private HsAudioFrameContract() {
    }

    public static int subframeBytes(int encodedOpusBytes) {
        return HEADER_BYTES + encodedOpusBytes;
    }
}
