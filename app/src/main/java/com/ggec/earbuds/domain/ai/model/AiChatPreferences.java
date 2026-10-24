package com.ggec.earbuds.domain.ai.model;

import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * 对话参数。{@code temperature}/{@code topP}/{@code maxOutputTokens} 为空表示沿用平台默认值，
 * 而不是填一个我们自己编的数字——不同平台对"默认"的定义本来就不同，
 * 我们替它决定只会让同一句话在不同模型上表现不一致。
 *
 * <p>{@code topP} 与 {@code temperature} 是两种不同的采样收窄方式：前者截断概率尾巴，
 * 后者调整分布陡峭度。一般只需要调一个，两个都调会让输出变得难以预测，所以界面上要把这点写清楚，
 * 而不是并排放两个滑块让人以为都该拉满。</p>
 *
 * <p>{@code contextMessages} 是每次请求最多带上的历史消息条数，0 表示全部。</p>
 */
public record AiChatPreferences(
        String systemPrompt,
        @Nullable Double temperature,
        @Nullable Double topP,
        @Nullable Integer maxOutputTokens,
        int contextMessages,
        boolean showReasoning,
        /** 全局参数总开关。关着时所有模型走自己的默认值，全局滑杆只是备着不被使用。 */
        boolean globalParamsEnabled) {

    public static final String DEFAULT_SYSTEM_PROMPT =
            "你是 GGEC 耳机的 AI 助手，熟悉蓝牙耳机的连接、配对、音效、降噪、续航和日常使用，也可以回答其他问题。"
                    + "请使用简体中文，回答简洁清晰；需要步骤时使用有序列表。";
    public static final int DEFAULT_CONTEXT_MESSAGES = 20;
    public static final int MAX_SYSTEM_PROMPT_LENGTH = 4000;

    public static final AiChatPreferences DEFAULT = new AiChatPreferences(
            DEFAULT_SYSTEM_PROMPT, null, null, null, DEFAULT_CONTEXT_MESSAGES, true, false);

    /** 兼容旧调用方：不带开关参数的构造一律视为"全局参数关闭"。 */
    public AiChatPreferences(String systemPrompt, @Nullable Double temperature, @Nullable Double topP,
                             @Nullable Integer maxOutputTokens, int contextMessages,
                             boolean showReasoning) {
        this(systemPrompt, temperature, topP, maxOutputTokens, contextMessages, showReasoning, false);
    }

    public AiChatPreferences {
        Objects.requireNonNull(systemPrompt, "systemPrompt");
        requireRange(temperature, 0, 2, "temperature");
        requireRange(topP, 0, 1, "topP");
        if (maxOutputTokens != null && maxOutputTokens < 1) {
            throw new IllegalArgumentException("maxOutputTokens must be positive");
        }
        if (contextMessages < 0) {
            throw new IllegalArgumentException("contextMessages must be >= 0");
        }
    }

    private static void requireRange(@Nullable Double value, double min, double max, String name) {
        if (value != null && (value < min || value > max)) {
            throw new IllegalArgumentException(name + " must be from " + min + " to " + max);
        }
    }

    public AiChatPreferences withGlobalParamsEnabled(boolean enabled) {
        return new AiChatPreferences(systemPrompt, temperature, topP, maxOutputTokens, contextMessages,
                showReasoning, enabled);
    }

    public AiChatPreferences withSystemPrompt(String systemPrompt) {
        return new AiChatPreferences(systemPrompt, temperature, topP, maxOutputTokens, contextMessages,
                showReasoning, globalParamsEnabled);
    }

    public AiChatPreferences withTemperature(@Nullable Double temperature) {
        return new AiChatPreferences(systemPrompt, temperature, topP, maxOutputTokens, contextMessages,
                showReasoning, globalParamsEnabled);
    }

    public AiChatPreferences withTopP(@Nullable Double topP) {
        return new AiChatPreferences(systemPrompt, temperature, topP, maxOutputTokens, contextMessages,
                showReasoning, globalParamsEnabled);
    }

    public AiChatPreferences withMaxOutputTokens(@Nullable Integer maxOutputTokens) {
        return new AiChatPreferences(systemPrompt, temperature, topP, maxOutputTokens, contextMessages,
                showReasoning, globalParamsEnabled);
    }

    public AiChatPreferences withContextMessages(int contextMessages) {
        return new AiChatPreferences(systemPrompt, temperature, topP, maxOutputTokens, contextMessages,
                showReasoning, globalParamsEnabled);
    }

    public AiChatPreferences withShowReasoning(boolean showReasoning) {
        return new AiChatPreferences(systemPrompt, temperature, topP, maxOutputTokens, contextMessages,
                showReasoning, globalParamsEnabled);
    }
}
