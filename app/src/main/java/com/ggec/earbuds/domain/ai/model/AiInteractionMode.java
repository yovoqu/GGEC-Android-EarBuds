package com.ggec.earbuds.domain.ai.model;

import java.util.Optional;

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
 * <p>所以两种模式各自保存"用哪个平台的哪个模型"（{@link AiSettings#textModel()} 与
 * {@link AiSettings#voice()}），切换模式不会把对方的选择顶掉。</p>
 */
public enum AiInteractionMode {

    /** 文本对话：接 DeepSeek 这类对话模型。 */
    TEXT,

    /** 语音交流：接实时全/多模态模型，一条连接直连。 */
    VOICE;

    /**
     * 新装应用的起点：文本对话不需要额外的实时语音模型，是更通用的入口。
     * 这是产品默认值的单点定义（{@link AiChatPreferences#DEFAULT} 用它），不是解析兜底。
     */
    public static AiInteractionMode defaultMode() {
        return TEXT;
    }

    /** 持久化值是枚举名；解析不出来就是空的，由解码方按文档损坏处理。 */
    public static Optional<AiInteractionMode> fromName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        for (AiInteractionMode mode : values()) {
            if (mode.name().equals(name)) {
                return Optional.of(mode);
            }
        }
        return Optional.empty();
    }
}
