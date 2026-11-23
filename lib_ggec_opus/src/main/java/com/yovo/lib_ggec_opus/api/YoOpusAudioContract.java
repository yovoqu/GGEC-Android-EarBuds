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

    /** 默认/推荐码率：32000 bps（既有取值，保持不动，其它代码/测试依赖） */
    public static final int BIT_RATE_DEFAULT = 32_000;
    public static final int BIT_RATE_MIN = 16_000;
    public static final int BIT_RATE_MAX = 64_000;

    /**
     * 由「单帧字节数 × 8 bit × 1000 ms/s ÷ 帧周期(ms)」推导的码率（CBR）。
     * 这些是线格式的物理真值，不可手写：40B/20ms 恒等于 16000 bps。
     */
    /** 16 kbps：40 * 8 * 1000 / 20 = 16000 */
    public static final int OPUS_BIT_RATE_16K = OPUS_BYTES_16K * 8 * 1000 / FRAME_PERIOD_MS;
    /** 32 kbps：80 * 8 * 1000 / 20 = 32000 */
    public static final int OPUS_BIT_RATE_32K = OPUS_BYTES_32K * 8 * 1000 / FRAME_PERIOD_MS;
    /** 64 kbps：160 * 8 * 1000 / 20 = 64000 */
    public static final int OPUS_BIT_RATE_64K = OPUS_BYTES_64K * 8 * 1000 / FRAME_PERIOD_MS;

    /** PCM 线码率：640 * 8 * 1000 / 20 = 256000 bps（16 kHz × 16 bit × 1ch） */
    public static final int PCM_BIT_RATE = PCM_FRAME_BYTES * 8 * 1000 / FRAME_PERIOD_MS;

    /**
     * 设备实际传输（wire）的 Opus 码率：16 kbps。
     * 解码器初始化应使用本值，与 40B/20ms 帧长严格一致；不再沿用 BMP 默认 32000。
     */
    public static final int WIRE_OPUS_BIT_RATE = OPUS_BIT_RATE_16K;

    /** 压缩比：PCM 256 kbps ÷ Opus 16 kbps = 16:1 */
    public static final int COMPRESSION_RATIO = PCM_BIT_RATE / WIRE_OPUS_BIT_RATE;

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
