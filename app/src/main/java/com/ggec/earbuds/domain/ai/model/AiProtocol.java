package com.ggec.earbuds.domain.ai.model;

public enum AiProtocol {
    CHAT_COMPLETIONS("chat_completions"),
    RESPONSES("responses"),
    MESSAGES("messages");

    private final String id;

    AiProtocol(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static AiProtocol fromId(String id, AiProtocol fallback) {
        for (AiProtocol protocol : values()) {
            if (protocol.id.equals(id)) {
                return protocol;
            }
        }
        return fallback;
    }
}
