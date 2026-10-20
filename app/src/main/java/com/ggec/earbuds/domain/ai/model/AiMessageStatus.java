package com.ggec.earbuds.domain.ai.model;

public enum AiMessageStatus {
    COMPLETE,
    STREAMING,
    STOPPED,
    ERROR;

    public static AiMessageStatus fromName(String name) {
        for (AiMessageStatus status : values()) {
            if (status.name().equals(name)) {
                return status;
            }
        }
        return COMPLETE;
    }
}
