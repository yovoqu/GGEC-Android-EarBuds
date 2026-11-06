package com.yovo.lib_ai.retrieval;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * token 计数结果。只回填端点真正给出的字段，缺失保持为空，绝不估算。
 */
public final class YoTokenCountResult {

    private final int inputTokens;
    @Nullable
    private final String model;
    private final String rawJson;

    public YoTokenCountResult(int inputTokens, @Nullable String model, @NonNull String rawJson) {
        this.inputTokens = inputTokens;
        this.model = model;
        this.rawJson = rawJson;
    }

    public int inputTokens() {
        return inputTokens;
    }

    @Nullable
    public String model() {
        return model;
    }

    @NonNull
    public String rawJson() {
        return rawJson;
    }
}
