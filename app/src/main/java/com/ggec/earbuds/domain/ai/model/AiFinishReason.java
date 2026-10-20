package com.ggec.earbuds.domain.ai.model;

public enum AiFinishReason {
    STOP,
    LENGTH,
    FILTERED,
    OTHER;

    public static AiFinishReason fromName(String name) {
        for (AiFinishReason reason : values()) {
            if (reason.name().equals(name)) {
                return reason;
            }
        }
        return STOP;
    }
}
