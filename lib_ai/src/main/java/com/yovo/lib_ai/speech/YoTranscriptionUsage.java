package com.yovo.lib_ai.speech;

import androidx.annotation.Nullable;

/**
 * 转写用量。
 *
 * <p>OpenAI 不同模型回报形状不同：token 计费给 {@code input_tokens/output_tokens/total_tokens}，
 * 按秒计费给 {@code seconds}。缺什么就留 {@code null}，不补 0。
 */
public final class YoTranscriptionUsage {

    @Nullable
    private final Integer inputTokens;
    @Nullable
    private final Integer outputTokens;
    @Nullable
    private final Integer totalTokens;
    @Nullable
    private final Double seconds;

    public YoTranscriptionUsage(
            @Nullable Integer inputTokens,
            @Nullable Integer outputTokens,
            @Nullable Integer totalTokens,
            @Nullable Double seconds) {
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.totalTokens = totalTokens;
        this.seconds = seconds;
    }

    @Nullable
    public Integer inputTokens() {
        return inputTokens;
    }

    @Nullable
    public Integer outputTokens() {
        return outputTokens;
    }

    @Nullable
    public Integer totalTokens() {
        return totalTokens;
    }

    /** 按秒计费时的音频秒数。 */
    @Nullable
    public Double seconds() {
        return seconds;
    }
}
