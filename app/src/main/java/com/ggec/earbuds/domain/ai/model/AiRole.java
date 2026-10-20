package com.ggec.earbuds.domain.ai.model;

public enum AiRole {
    USER,
    ASSISTANT;

    public static AiRole fromName(String name) {
        return ASSISTANT.name().equals(name) ? ASSISTANT : USER;
    }
}
