package com.yovo.lib_ggec_opus.api;

/**
 * 音频几何与码率契约：采集/编码/解码/落盘共用的一套取值（唯一信源）。
 *
 * <p>标准格式：16 kHz / 单声道 / 16-bit PCM / 20 ms 帧周期。</p>
 *
 * <p>下游模块（如 {@code lib_ggec_audio}）只引用本契约推导自己的线格式常量，
 * 不再各自重复字面量；方向固定为「下游 -> 本契约」，本模块不反向依赖任何下游。</p>
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

    /** 每毫秒 PCM 字节数：640 / 20 = 32（时长换算只走这个常量，不再写字面量） */
    public static final int PCM_BYTES_PER_MILLISECOND = PCM_FRAME_BYTES / FRAME_PERIOD_MS;

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

    /** 解码输入长度：Opus 单帧按实际包长送入 native，不按码率推包长、不扫尾零。 */
    public static int decodeInputLength(byte[] packet) {
        return packet == null ? 0 : packet.length;
    }

    /** PCM 字节数换算录制时长（毫秒）。 */
    public static long durationMsFor(long pcmBytes) {
        return pcmBytes / PCM_BYTES_PER_MILLISECOND;
    }
}
