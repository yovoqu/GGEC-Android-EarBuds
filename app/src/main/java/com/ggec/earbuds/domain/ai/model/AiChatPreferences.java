package com.ggec.earbuds.domain.ai.model;

import java.util.Objects;

/**
 * 对话偏好：系统提示词、"要不要显示思考过程"，以及设置页最后停留的分段。
 *
 * <p>温度、top_p、最大输出、上下文条数曾经也在这里，现在统一由
 * {@link AiGenerationDefaults} 在发请求时给定。它们是产品参数，不是用户偏好：
 * 摆在设置页只会让人以为调了会不一样，而实际几乎没人会动。</p>
 *
 * <p>{@code lastSettingsMode} 是视图偏好，但它只有一份真源：跟着设置文档一起读写，
 * 不再有第二个系统偏好文件存放它。它不参与请求，也不影响任何模型选择。</p>
 */
public record AiChatPreferences(
        String systemPrompt,
        /** 推理型模型返回的思考内容要不要在气泡里展开。 */
        boolean showReasoning,
        /** 设置页上次停在哪个分段。 */
        AiInteractionMode lastSettingsMode) {

    public static final String DEFAULT_SYSTEM_PROMPT =
            "你是 GGEC 耳机的 AI 助手，熟悉蓝牙耳机的连接、配对、音效、降噪、续航和日常使用，也可以回答其他问题。"
                    + "请使用简体中文，回答简洁清晰；需要步骤时使用有序列表。";
    public static final int MAX_SYSTEM_PROMPT_LENGTH = 4000;

    public static final AiChatPreferences DEFAULT = new AiChatPreferences(
            DEFAULT_SYSTEM_PROMPT, true, AiInteractionMode.defaultMode());

    public AiChatPreferences {
        Objects.requireNonNull(systemPrompt, "systemPrompt");
        Objects.requireNonNull(lastSettingsMode, "lastSettingsMode");
    }

    public AiChatPreferences withSystemPrompt(String systemPrompt) {
        return new AiChatPreferences(systemPrompt, showReasoning, lastSettingsMode);
    }

    public AiChatPreferences withShowReasoning(boolean showReasoning) {
        return new AiChatPreferences(systemPrompt, showReasoning, lastSettingsMode);
    }

    public AiChatPreferences withLastSettingsMode(AiInteractionMode lastSettingsMode) {
        return new AiChatPreferences(systemPrompt, showReasoning, lastSettingsMode);
    }
}
