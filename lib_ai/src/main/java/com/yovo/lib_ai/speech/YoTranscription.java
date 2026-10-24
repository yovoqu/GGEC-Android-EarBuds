package com.yovo.lib_ai.speech;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 一次转写的结果。
 *
 * <p>字段取舍：{@code text} 一定存在，静音片段合法地是空串——空串不是失败，
 * 失败一律走异常，调用方不需要用"文本是否为空"反推请求成败。
 * {@code language} 与 {@code durationSeconds} 只有在网关回报时才非空：
 * OpenAI 官方会带 {@code duration}，不少兼容网关只回 {@code text}，
 * 缺字段一律留 null，不用采样数反算时长假装知道。
 */
public final class YoTranscription {

    private final String text;
    @Nullable
    private final String language;
    @Nullable
    private final Double durationSeconds;
    private final String rawJson;

    public YoTranscription(
            @NonNull String text,
            @Nullable String language,
            @Nullable Double durationSeconds,
            @NonNull String rawJson) {
        this.text = text;
        this.language = language;
        this.durationSeconds = durationSeconds;
        this.rawJson = rawJson;
    }

    /** 识别文本。静音时为 {@code ""}，不会是 null。 */
    @NonNull
    public String text() {
        return text;
    }

    /** 网关回报的语种（如 {@code zh}）；网关没给时为 null。 */
    @Nullable
    public String language() {
        return language;
    }

    /** 网关回报的音频时长（秒）；网关没给时为 null，客户端不自己算。 */
    @Nullable
    public Double durationSeconds() {
        return durationSeconds;
    }

    /** 原始响应体，排查网关差异时用；不含请求头，也不含 API Key。 */
    @NonNull
    public String rawJson() {
        return rawJson;
    }
}
