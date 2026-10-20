package com.ggec.earbuds.domain.ai.model;

import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * 对话参数。{@code temperature} 和 {@code maxOutputTokens} 为空表示沿用平台默认值。
 * {@code contextMessages} 是每次请求最多带上的历史消息条数，0 表示全部。
 */
public record AiChatPreferences(
        String systemPrompt,
        @Nullable Double temperature,
        @Nullable Integer maxOutputTokens,
        int contextMessages,
        boolean showReasoning) {

    public static final String DEFAULT_SYSTEM_PROMPT =
            "你是 GGEC 耳机的 AI 助手，熟悉蓝牙耳机的连接、配对、音效、降噪、续航和日常使用，也可以回答其他问题。"
                    + "请使用简体中文，回答简洁清晰；需要步骤时使用有序列表。";
    public static final int DEFAULT_CONTEXT_MESSAGES = 20;
    public static final int MAX_SYSTEM_PROMPT_LENGTH = 4000;

    public static final AiChatPreferences DEFAULT =
            new AiChatPreferences(DEFAULT_SYSTEM_PROMPT, null, null, DEFAULT_CONTEXT_MESSAGES, true);

    public AiChatPreferences {
        Objects.requireNonNull(systemPrompt, "systemPrompt");
        if (temperature != null && (temperature < 0 || temperature > 2)) {
            throw new IllegalArgumentException("temperature must be from 0 to 2");
        }
        if (maxOutputTokens != null && maxOutputTokens < 1) {
            throw new IllegalArgumentException("maxOutputTokens must be positive");
        }
        if (contextMessages < 0) {
            throw new IllegalArgumentException("contextMessages must be >= 0");
        }
    }

    public AiChatPreferences withSystemPrompt(String systemPrompt) {
        return new AiChatPreferences(systemPrompt, temperature, maxOutputTokens, contextMessages, showReasoning);
    }

    public AiChatPreferences withTemperature(@Nullable Double temperature) {
        return new AiChatPreferences(systemPrompt, temperature, maxOutputTokens, contextMessages, showReasoning);
    }

    public AiChatPreferences withMaxOutputTokens(@Nullable Integer maxOutputTokens) {
        return new AiChatPreferences(systemPrompt, temperature, maxOutputTokens, contextMessages, showReasoning);
    }

    public AiChatPreferences withContextMessages(int contextMessages) {
        return new AiChatPreferences(systemPrompt, temperature, maxOutputTokens, contextMessages, showReasoning);
    }

    public AiChatPreferences withShowReasoning(boolean showReasoning) {
        return new AiChatPreferences(systemPrompt, temperature, maxOutputTokens, contextMessages, showReasoning);
    }
}
