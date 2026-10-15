package com.yovo.lib_ai.responses;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class YoResponseUsage {

    private final int inputTokens;
    private final int outputTokens;
    private final int totalTokens;

    public YoResponseUsage(int inputTokens, int outputTokens, int totalTokens) {
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.totalTokens = totalTokens;
    }

    public int inputTokens() {
        return inputTokens;
    }

    public int outputTokens() {
        return outputTokens;
    }

    public int totalTokens() {
        return totalTokens;
    }
}
