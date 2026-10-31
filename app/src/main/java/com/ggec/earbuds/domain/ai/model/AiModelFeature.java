package com.ggec.earbuds.domain.ai.model;

/**
 * 模型具备的能力。平台页据此把实时模型从文本对话列表里排除：
 * 它们走不了 Chat Completions，列出来只会让人选中后收到 404。
 */
public enum AiModelFeature {

    VISION,

    REASONING,

    /**
     * 支持实时语音（Realtime）：一条 WebSocket 同时上下行音频，服务端自己断句。
     * 这类模型通常不会出现在 {@code /models} 列表里，需要手动添加 id。
     */
    REALTIME_AUDIO
}
