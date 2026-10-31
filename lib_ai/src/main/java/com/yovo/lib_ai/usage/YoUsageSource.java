package com.yovo.lib_ai.usage;

/**
 * 一次用量记录来自哪条调用链路。
 *
 * <p>分链路记账的理由：同一个平台下，对话、测速、语音的计费口径和单价可能不同，
 * 混在一起算总数会让"这个月花在哪"无法回答。
 */
public enum YoUsageSource {
    /** OpenAI 风格的 {@code /chat/completions}。 */
    CHAT_COMPLETIONS,
    /** OpenAI 风格的 {@code /responses}。 */
    RESPONSES,
    /** Anthropic 风格的 {@code /messages}。 */
    ANTHROPIC_MESSAGES,
    /** 统一补全层 {@code YoCompletions}，它内部按协议转发到上面三者之一。 */
    COMPLETIONS,
    /** 测速探针。只测首字时没有输出 token，费用只剩首字那一段。 */
    PROBE,
    /** 实时语音会话。一轮 {@code response.done} 记一笔，不按音频帧记。 */
    REALTIME,
    /** 其他入口（语音转写等），暂未细分。 */
    OTHER
}
