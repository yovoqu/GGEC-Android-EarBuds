package com.ggec.earbuds.domain.ai.model;

import java.util.Objects;

/**
 * 对话偏好：只剩系统提示词和"要不要显示思考过程"。
 *
 * <p>温度、top_p、最大输出、上下文条数曾经也在这里，现在统一由
 * {@link AiGenerationDefaults} 在发请求时给定。它们是产品参数，不是用户偏好：
 * 摆在设置页只会让人以为调了会不一样，而实际几乎没人会动。</p>
 */
public record AiChatPreferences(
        String systemPrompt,
        /** 推理型模型返回的思考内容要不要在气泡里展开。 */
        boolean showReasoning) {

    public static final String DEFAULT_SYSTEM_PROMPT =
            "你是 GGEC 耳机的 AI 助手，熟悉蓝牙耳机的连接、配对、音效、降噪、续航和日常使用，也可以回答其他问题。"
                    + "请使用简体中文，回答简洁清晰；需要步骤时使用有序列表。";
    public static final int MAX_SYSTEM_PROMPT_LENGTH = 4000;

    public static final AiChatPreferences DEFAULT = new AiChatPreferences(DEFAULT_SYSTEM_PROMPT, true);

    public AiChatPreferences {
        Objects.requireNonNull(systemPrompt, "systemPrompt");
    }

    public AiChatPreferences withSystemPrompt(String systemPrompt) {
        return new AiChatPreferences(systemPrompt, showReasoning);
    }

    public AiChatPreferences withShowReasoning(boolean showReasoning) {
        return new AiChatPreferences(systemPrompt, showReasoning);
    }
}
