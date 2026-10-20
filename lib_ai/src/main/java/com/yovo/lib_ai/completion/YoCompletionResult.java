package com.yovo.lib_ai.completion;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.api.YoProtocol;

/**
 * 一次补全的最终结果。{@link #text()} 与流式正文增量拼起来完全一致。
 */
public final class YoCompletionResult {

    private final YoProtocol protocol;
    private final String text;
    @Nullable
    private final String reasoning;
    private final YoFinishReason finishReason;
    @Nullable
    private final YoCompletionUsage usage;
    @Nullable
    private final String model;
    @Nullable
    private final String responseId;

    public YoCompletionResult(
            @NonNull YoProtocol protocol,
            @NonNull String text,
            @Nullable String reasoning,
            @NonNull YoFinishReason finishReason,
            @Nullable YoCompletionUsage usage,
            @Nullable String model,
            @Nullable String responseId) {
        this.protocol = protocol;
        this.text = text;
        this.reasoning = reasoning == null || reasoning.isEmpty() ? null : reasoning;
        this.finishReason = finishReason;
        this.usage = usage;
        this.model = model;
        this.responseId = responseId;
    }

    @NonNull
    public YoProtocol protocol() {
        return protocol;
    }

    @NonNull
    public String text() {
        return text;
    }

    @Nullable
    public String reasoning() {
        return reasoning;
    }

    @NonNull
    public YoFinishReason finishReason() {
        return finishReason;
    }

    @Nullable
    public YoCompletionUsage usage() {
        return usage;
    }

    /**
     * 平台实际应答的模型名，可能和请求里的别名不同。
     */
    @Nullable
    public String model() {
        return model;
    }

    @Nullable
    public String responseId() {
        return responseId;
    }
}
