package com.ggec.earbuds.domain.ai.model;

/**
 * 模型具备的能力。设置页按它把模型分给两种接入形态：
 * 文本模式要能对话，语音模式要能直接收发实时音频。
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
