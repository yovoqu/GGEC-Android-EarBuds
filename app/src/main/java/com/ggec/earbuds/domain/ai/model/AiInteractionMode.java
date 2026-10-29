package com.ggec.earbuds.domain.ai.model;

/**
 * AI 的接入形态：文本对话，还是语音交流。
 *
 * <p>两者不是同一个模型的两套参数，而是两类完全不同的能力：</p>
 * <ul>
 *   <li>{@link #TEXT}：DeepSeek 这类对话模型，走 Chat Completions / Messages / Responses，
 *       一问一答都是文字；</li>
 *   <li>{@link #VOICE}：全/多模态实时模型，一条 WebSocket 同时上下行音频，
 *       转写、回答、发声都由模型服务端完成。</li>
 * </ul>
 *
 * <p>所以两种模式各自保存"用哪个平台的哪个模型"（{@link AiSettings#selection(AiInteractionMode)}），
 * 切换模式不会把对方的选择顶掉。APP 侧不再拼 ASR → LLM → TTS 三段链路——
 * 那条链路在语音模式下没有对应的模型可选。</p>
 */
public enum AiInteractionMode {

    /** 文本对话：接 DeepSeek 这类对话模型。 */
    TEXT,

    /** 语音交流：接实时全/多模态模型，一条连接直连。 */
    VOICE;

    /** 旧设置文档里没有这个字段时用它：文本对话不需要额外的实时语音模型，是更通用的起点。 */
    public static AiInteractionMode defaultMode() {
        return TEXT;
    }

    public static AiInteractionMode fromName(String name) {
        if (name == null) {
            return defaultMode();
        }
        for (AiInteractionMode mode : values()) {
            if (mode.name().equalsIgnoreCase(name)) {
                return mode;
            }
        }
        return defaultMode();
    }
}
