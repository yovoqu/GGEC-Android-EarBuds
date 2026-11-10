package com.yovo.lib_ai.speech;

import androidx.annotation.Nullable;

/**
 * 一次合成结果。
 *
 * <p>两种载体：OpenAI 直接回二进制音频，落在 {@link #audioBytes()}；
 * Qwen 回 {@code output.audio.url}（24h 有效）或 SSE 的 {@code output.audio.data}（Base64）。
 * 前者是 {@code null} 时音频在远端，调用方需要尽快下载落盘——URL 会过期。
 */
public final class YoSynthesisResult {

    @Nullable
    private final byte[] audio;
    @Nullable
    private final String mimeType;
    @Nullable
    private final String audioUrl;
    @Nullable
    private final String requestId;
    @Nullable
    private final String rawJson;

    public YoSynthesisResult(
            @Nullable byte[] audio,
            @Nullable String mimeType,
            @Nullable String audioUrl,
            @Nullable String requestId,
            @Nullable String rawJson) {
        this.audio = audio == null ? null : audio.clone();
        this.mimeType = mimeType;
        this.audioUrl = audioUrl;
        this.requestId = requestId;
        this.rawJson = rawJson;
    }

    /** 是否有本地音频字节。远端 URL 形态时返回 {@code false}。 */
    public boolean hasAudio() {
        return audio != null && audio.length > 0;
    }

    /** 本地音频字节（防御性拷贝）；远端 URL 形态时为 {@code null}。 */
    @Nullable
    public byte[] audioBytes() {
        return audio == null ? null : audio.clone();
    }

    /** 音频容器类型，如 {@code audio/mpeg}、{@code audio/wav}；未知为 {@code null}。 */
    @Nullable
    public String mimeType() {
        return mimeType;
    }

    /** 远端音频地址（Qwen 非流式）；本地二进制形态时为 {@code null}。 */
    @Nullable
    public String audioUrl() {
        return audioUrl;
    }

    @Nullable
    public String requestId() {
        return requestId;
    }

    /** 原始响应体，排查网关差异用；不含请求头与 API Key。 */
    @Nullable
    public String rawJson() {
        return rawJson;
    }
}
