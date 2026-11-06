package com.yovo.lib_ai.speech;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * 一次转写的结果。
 *
 * <p>字段取舍：{@code text} 一定存在，静音片段合法地是空串——空串不是失败，
 * 失败一律走异常，调用方不需要用"文本是否为空"反推请求成败。
 * {@code language} 与 {@code durationSeconds} 只有在网关回报时才非空：
 * OpenAI 官方会带 {@code duration}，不少兼容网关只回 {@code text}，
 * 缺字段一律留 null，不用采样数反算时长假装知道。
 *
 * <p>F12 扩展：{@code segments/words/speakers/usage} 只在 {@code verbose_json} /
 * {@code diarized_json} 等格式里出现；旧的 {@code json} 格式这四个都是空列表或 null。
 * 老构造器 {@link #YoTranscription(String, String, Double, String)} 行为不变，方便既有调用点。
 */
public final class YoTranscription {

    private final String text;
    @Nullable
    private final String language;
    @Nullable
    private final Double durationSeconds;
    private final String rawJson;
    @NonNull
    private final List<YoSegment> segments;
    @NonNull
    private final List<YoWord> words;
    @NonNull
    private final List<String> speakers;
    @Nullable
    private final YoTranscriptionUsage usage;

    /** 旧构造器：只有文本、语种、时长与原始体，其余为空。 */
    public YoTranscription(
            @NonNull String text,
            @Nullable String language,
            @Nullable Double durationSeconds,
            @NonNull String rawJson) {
        this(text, language, durationSeconds, rawJson, List.of(), List.of(), List.of(), null);
    }

    public YoTranscription(
            @NonNull String text,
            @Nullable String language,
            @Nullable Double durationSeconds,
            @NonNull String rawJson,
            @NonNull List<YoSegment> segments,
            @NonNull List<YoWord> words,
            @NonNull List<String> speakers,
            @Nullable YoTranscriptionUsage usage) {
        this.text = text;
        this.language = language;
        this.durationSeconds = durationSeconds;
        this.rawJson = rawJson;
        this.segments = List.copyOf(segments);
        this.words = List.copyOf(words);
        this.speakers = List.copyOf(speakers);
        this.usage = usage;
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

    /** 分段；{@code json} 格式或网关未给时为空的不可变列表。 */
    @NonNull
    public List<YoSegment> segments() {
        return segments;
    }

    /** 顶层词级时间戳；网关未给时为空的不可变列表。 */
    @NonNull
    public List<YoWord> words() {
        return words;
    }

    /** 去重后的说话人标签；未做说话人分离时为空列表。 */
    @NonNull
    public List<String> speakers() {
        return speakers;
    }

    /** 用量；网关未给时为 null。 */
    @Nullable
    public YoTranscriptionUsage usage() {
        return usage;
    }
}
