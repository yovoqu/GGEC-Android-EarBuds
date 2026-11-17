package com.yovo.lib_ggec_audio;

import com.yovo.lib_ggec_opus.api.YoOpusAudioContract;

/**
 * 音频帧结构与码率契约常量：空口帧几何的唯一信源。
 *
 * <p>BLE / SPP 音频帧 = 4 字节小端 dataIndex + N 字节 Opus 编码帧（旧线格式头是 8 字节）。
 * 采样侧固定 16 kHz / 单声道 / 16-bit PCM / 20 ms 一帧，因此一帧 PCM 恒为 640 字节。</p>
 *
 * <p>几何与码率**不在本类重复字面量**：一律从 {@link YoOpusAudioContract} 推导
 * （依赖方向固定为 audio → opus/api，opus 不反向依赖本模块）。
 * 本类只负责「线格式」（头长、Notify 包长、每包帧数）。</p>
 */
public final class AudioFrameContract {

    public static final int SAMPLE_RATE = YoOpusAudioContract.SAMPLE_RATE;
    public static final int CHANNEL_COUNT = YoOpusAudioContract.CHANNEL_COUNT;
    public static final int FRAME_PERIOD_MS = YoOpusAudioContract.FRAME_PERIOD_MS;
    public static final int PCM_SAMPLES_PER_FRAME = YoOpusAudioContract.PCM_SAMPLES_PER_FRAME;
    public static final int PCM_FRAME_BYTES = YoOpusAudioContract.PCM_FRAME_BYTES;

    public static final int HEADER_BYTES = 4;
    public static final int HEADER_LEGACY_BYTES = 8;
    public static final int STARTING_DATA_INDEX = 0;

    public static final int OPUS_BYTES_16K = YoOpusAudioContract.OPUS_BYTES_16K;
    public static final int OPUS_BYTES_32K = YoOpusAudioContract.OPUS_BYTES_32K;
    public static final int OPUS_BYTES_64K = YoOpusAudioContract.OPUS_BYTES_64K;

    public static final int BIT_RATE_DEFAULT = YoOpusAudioContract.BIT_RATE_DEFAULT;
    public static final int BIT_RATE_16K = YoOpusAudioContract.BIT_RATE_MIN;
    public static final int BIT_RATE_32K = YoOpusAudioContract.BIT_RATE_DEFAULT;

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

    private AudioFrameContract() {
    }

    public static int subframeBytes(int encodedOpusBytes) {
        return HEADER_BYTES + encodedOpusBytes;
    }
}
