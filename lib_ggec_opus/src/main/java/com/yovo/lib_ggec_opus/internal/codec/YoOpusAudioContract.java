package com.yovo.lib_ggec_opus.internal.codec;

/**
 * Opus 与 PCM 音频几何契约规范（统一信源）。
 *
 * <p>标准格式：16 kHz / 单声道 / 16-bit PCM / 20 ms 帧周期。</p>
 */
public final class YoOpusAudioContract {

    /** 采样率（Hz）：16000 */
    public static final int SAMPLE_RATE = 16_000;

    /** 声道数：1（单声道） */
    public static final int CHANNEL_COUNT = 1;

    /** 帧周期（毫秒）：20 ms */
    public static final int FRAME_PERIOD_MS = 20;

    /** 单帧 PCM 采样点数：16000 * 20 / 1000 = 320 */
    public static final int PCM_SAMPLES_PER_FRAME = SAMPLE_RATE * FRAME_PERIOD_MS / 1000;

    /** 单帧 16-bit PCM 字节数：320 * 2 = 640 字节 */
    public static final int PCM_FRAME_BYTES = PCM_SAMPLES_PER_FRAME * 2;

    /** 常见 Opus 编码单帧字节数（CBR 模式下）：16kbps -> 40B, 32kbps -> 80B, 64kbps -> 160B */
    public static final int OPUS_BYTES_16K = 40;
    public static final int OPUS_BYTES_32K = 80;
    public static final int OPUS_BYTES_64K = 160;

    /** 默认/推荐码率：32000 bps */
    public static final int BIT_RATE_DEFAULT = 32_000;
    public static final int BIT_RATE_MIN = 16_000;
    public static final int BIT_RATE_MAX = 64_000;

    /** MP3 导出推荐配置 */
    public static final int MP3_BITRATE_KBPS = 64;
    public static final int MP3_QUALITY = 5;

    private YoOpusAudioContract() {
        throw new AssertionError("No instances of YoOpusAudioContract");
    }

    public static int decodeInputLength(byte[] packet) {
        return packet == null ? 0 : packet.length;
    }
}
