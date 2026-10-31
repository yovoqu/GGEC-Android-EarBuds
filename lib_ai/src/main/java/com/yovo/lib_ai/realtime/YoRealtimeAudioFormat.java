package com.yovo.lib_ai.realtime;

import androidx.annotation.NonNull;

/**
 * 套接字上那一段音频的格式，不是麦克风的采集格式。
 *
 * <p>两边经常不一样：耳机麦按 16kHz 采，app 重采样成 24kHz 再 {@code append}。
 * 协议要声明的是重采样之后的速率。把采集速率写进 {@code session.update}，
 * 服务端会把 24kHz 的字节当成 16kHz，声音发飘。</p>
 */
public final class YoRealtimeAudioFormat {

    /** 线上编码。G.711 固定 8kHz、一字节一个采样。 */
    public enum Encoding {
        PCM16,
        PCMU,
        PCMA
    }

    @NonNull
    private final Encoding encoding;
    private final int sampleRateHz;

    private YoRealtimeAudioFormat(@NonNull Encoding encoding, int sampleRateHz) {
        this.encoding = encoding;
        this.sampleRateHz = sampleRateHz;
    }

    /**
     * 16-bit 小端单声道 PCM。OpenAI GA 接受 16000 和 24000；通义还接受 8000 和 48000。
     * 这里不替厂商拦速率，拦了会让新的合法速率在库里先炸。
     */
    @NonNull
    public static YoRealtimeAudioFormat pcm16(int sampleRateHz) {
        if (sampleRateHz <= 0) {
            throw new IllegalArgumentException("sampleRateHz must be positive");
        }
        return new YoRealtimeAudioFormat(Encoding.PCM16, sampleRateHz);
    }

    @NonNull
    public static YoRealtimeAudioFormat pcmu() {
        return new YoRealtimeAudioFormat(Encoding.PCMU, 8_000);
    }

    @NonNull
    public static YoRealtimeAudioFormat pcma() {
        return new YoRealtimeAudioFormat(Encoding.PCMA, 8_000);
    }

    @NonNull
    public Encoding encoding() {
        return encoding;
    }

    public int sampleRateHz() {
        return sampleRateHz;
    }

    /** PCM16 两字节一个采样；G.711 一字节一个采样。播放进度用它把字节换算成毫秒。 */
    public int bytesPerFrame() {
        return encoding == Encoding.PCM16 ? 2 : 1;
    }

    @NonNull
    @Override
    public String toString() {
        return encoding + "@" + sampleRateHz;
    }
}
