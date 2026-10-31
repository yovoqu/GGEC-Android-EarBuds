package com.yovo.lib_ai.realtime;

/**
 * 同一条实时会话在不同厂商线上的写法。
 *
 * <p>传输层不关心这些差别。差别全部收在 {@link RealtimeProtocol}：
 * 握手头、{@code session.update} 的形状、以及要不要等 {@code session.updated}
 * 再放行缓存音频。</p>
 */
public enum YoRealtimeDialect {

    /**
     * OpenAI Realtime beta：扁平的 {@code input_audio_format=pcm16}，
     * 握手带 {@code OpenAI-Beta: realtime=v1}。采样率写不进协议，字节必须是 24kHz PCM16。
     * 这是当前默认，已有网关按这个字段集工作。
     */
    OPENAI_BETA,

    /**
     * OpenAI Realtime GA：{@code session.type=realtime}，采样率在
     * {@code audio.input.format.rate}。不发送 beta 头。
     * 要等 {@code session.updated} 再送音频，避免第一段按服务端默认格式被解释。
     */
    OPENAI_GA,

    /**
     * 通义 Omni-Realtime：{@code audio.*.format.sample_rate}，模态字段是 {@code modalities}。
     * 不发送 beta 头。和 beta 一样，连上就放行音频——部分兼容网关不回 {@code session.updated}。
     */
    QWEN;

    /**
     * GA 会在 {@code session.update} 被确认前就收到音频，并按默认 24kHz 解释。
     * 其它方言的确认帧不稳定，缓存音频不能干等。
     */
    public boolean holdsAudioUntilConfigured() {
        return this == OPENAI_GA;
    }
}
