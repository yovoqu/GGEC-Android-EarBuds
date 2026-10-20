package com.ggec.earbuds.domain.ai.model;

public enum AiFailureKind {
    NO_PLATFORM,
    NO_MODEL,
    AUTHENTICATION,
    PERMISSION,
    QUOTA,
    RATE_LIMITED,
    NOT_FOUND,
    BAD_REQUEST,
    SERVER,
    NETWORK,
    TIMEOUT,
    CANCELLED,
    PROTOCOL,
    STORAGE,
    UNKNOWN;

    public boolean retryable() {
        return this == RATE_LIMITED || this == SERVER || this == NETWORK || this == TIMEOUT;
    }

    public static AiFailureKind fromName(String name) {
        for (AiFailureKind kind : values()) {
            if (kind.name().equals(name)) {
                return kind;
            }
        }
        return UNKNOWN;
    }
}
