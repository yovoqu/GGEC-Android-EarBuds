package com.ggec.earbuds.domain.ai.model;

public record AiUsage(int inputTokens, int outputTokens) {

    public int totalTokens() {
        return inputTokens + outputTokens;
    }
}
