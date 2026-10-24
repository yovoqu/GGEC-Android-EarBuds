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
    /** 平台里没有具备该能力的模型（例如要求看图，但当前平台只支持文本）。 */
    UNSUPPORTED_CAPABILITY,
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
