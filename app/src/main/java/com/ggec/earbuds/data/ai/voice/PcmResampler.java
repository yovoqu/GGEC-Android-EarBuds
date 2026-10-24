package com.ggec.earbuds.data.ai.voice;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;

/**
 * 把 16kHz 的 PCM16 升到 24kHz，供全双工链路下行/上行对齐使用。
 *
 * <p><b>为什么必须重采样，而不是把 16k 的字节直接发给服务端</b>：Realtime 的 {@code pcm16} 只有一个档位，
 * 服务端按 24kHz 解释这些字节；协议里没有"这段是 16k"的字段。送 16k 过去，服务端会当成慢了 1.5 倍的
 * 声音——听起来音调变低、语速变慢，识别质量也跟着掉。所以采集端保持 16k（耳机麦克风与转写链路都用它），
 * 到了上行这一层再升到 24k。</p>
 *
 * <p><b>为什么是流式的、要记住跨帧状态</b>：采集一帧 20ms，帧与帧之间不能各算各的。16k→24k 的步长是
 * 2/3 个输入采样，一帧 320 个输入采样正好产生 480 个输出采样后落回整数位置；但如果每帧都从 0 重新起步，
 * 输出会变成"每帧 480 个、但内部相位错开"的拼接，表现为一种周期性抖动。这里把帧末尾的
 * 浮点相位带进下一帧，让重采样网格在整通电话里是连续的一条时间轴。</p>
 *
 * <p><b>为什么丢奇数长度的最后一字节</b>：PCM16 是两字节一个采样，奇数长度说明最后半个采样被截断了。
 * 留着它会让之后所有字节错位半格，解出来的是一段持续的沙沙声——比丢一个字节难查得多。</p>
 *
 * <p>线程：不是线程安全的。调用方要保证同一时刻只有一条线程在用（本工程里是通话的状态执行器）。</p>
 */
final class PcmResampler {

    /** 采集链路统一 16kHz（见 {@code AiAudioCapture#start}）。 */
    static final int INPUT_RATE_HZ = 16_000;

    /** 全双工下行/上行的原生档位：24kHz。 */
    static final int OUTPUT_RATE_HZ = 24_000;

    private static final int MIN_SAMPLE = Short.MIN_VALUE;
    private static final int MAX_SAMPLE = Short.MAX_VALUE;

    /**
     * 相位用"三分之一个输入采样"做单位，而不是用 double。
     *
     * <p>16000 → 24000 的比例是 2/3，{@code double} 存不下 2/3：一帧 320 个采样累加 480 步之后，
     * 浮点误差会让最后一次比较落在 319.99999999999997 —— 比 320 小一点点，于是多吐出一个采样。
     * 单帧看起来只差 2 个字节，但每帧都错一次，一小时下来就是可闻的音调偏移。
     * 换成整数单位（每步 +2、帧长 3×采样数）之后，比例是精确的 1.5 倍，没有任何累积误差。</p>
     */
    private static final int PHASE_UNITS_PER_SAMPLE = 3;

    /** 每个输出采样前进的相位单位数：2/3 采样 = 2 个三分之一单位。 */
    private static final int PHASE_STEP = 2;

    /** 上一帧结束时残留的相位（单位：1/3 输入采样，取值 0..2），保证跨帧网格连续。 */
    private long phaseUnits;

    /**
     * 重采样一段。
     *
     * <p>长度关系：{@code 输出字节数 = 输入字节数 × 1.5}（输入为整帧时严格成立，例如 640 → 960）。
     * 奇数输入长度会先丢掉半个采样，所以每帧可能比 1.5 倍少两个字节，下一帧靠残留相位补回来。</p>
     *
     * <p>空数组返回空数组：调用方（采集回调）可能在收尾阶段送进来一帧空数据，不该因此抛异常。</p>
     */
    @NonNull
    byte[] resample(@NonNull byte[] pcm16) {
        int samples = pcm16.length / 2;
        if (samples == 0) {
            return new byte[0];
        }
        // 预分配按 1.5 倍再留一点余量，避免每帧都扩容。
        ByteArrayOutputStream out = new ByteArrayOutputStream(samples * 3 + 8);
        long cursor = phaseUnits;
        long end = (long) samples * PHASE_UNITS_PER_SAMPLE;
        while (cursor < end) {
            int leftIndex = (int) (cursor / PHASE_UNITS_PER_SAMPLE);
            double fraction = (cursor % PHASE_UNITS_PER_SAMPLE) / (double) PHASE_UNITS_PER_SAMPLE;
            int left = sampleAt(pcm16, leftIndex);
            // 帧尾没有"下一个"采样时保持最后一个值：等价于零阶保持，最多影响一个网格点，
            // 20ms 一帧里听不出来；换成跨帧再取下一个字节，会让上行延迟多出一整帧。
            int right = leftIndex + 1 < samples ? sampleAt(pcm16, leftIndex + 1) : left;
            int value = (int) Math.round(left + (right - left) * fraction);
            writeLittleEndian(out, Math.max(MIN_SAMPLE, Math.min(MAX_SAMPLE, value)));
            cursor += PHASE_STEP;
        }
        // 多走的那一步留给下一帧（帧长不是 3 的倍数时会出现半个网格点的偏差）。
        phaseUnits = cursor - end;
        return out.toByteArray();
    }

    /** 回到初始相位。换一路通话时调用，避免上一通电话的尾巴影响下一通。 */
    void reset() {
        phaseUnits = 0L;
    }

    private static int sampleAt(byte[] pcm16, int index) {
        int offset = index * 2;
        return (short) ((pcm16[offset] & 0xFF) | (pcm16[offset + 1] << 8));
    }

    private static void writeLittleEndian(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
    }
}
