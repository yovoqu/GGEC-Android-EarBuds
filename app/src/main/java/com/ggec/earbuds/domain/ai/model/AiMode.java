package com.ggec.earbuds.domain.ai.model;

/**
 * AI 的交互形态。
 *
 * <p>两种模式决定的是**链路**，不是界面：通话页在两种模式下长得一样，
 * 但 OMNI 走一条 WebSocket 双向音频，CASCADED 走"转写 → 对话 → 合成"三段。
 * 所以这个枚举属于领域，不属于界面——界面只负责把当前模式显示出来。</p>
 *
 * <p>加新模式时只动两处：这里加一个值，{@code AiVoiceStrategy} 加一个实现。
 * 不允许在页面里按模式写 if。</p>
 */
public enum AiMode {

    /** 级联：录音 → 语音识别 → 对话模型 → 语音合成。任何对话模型都能用。 */
    CASCADED,

    /** 全双工：一条 WebSocket 同时上下行音频，边说边收，可被打断。 */
    OMNI;

    /** 旧设置文档里没有这个字段时用它：级联更通用，不需要额外的实时语音模型。 */
    public static AiMode defaultMode() {
        return CASCADED;
    }

    public static AiMode fromName(String name) {
        if (name == null) {
            return defaultMode();
        }
        for (AiMode mode : values()) {
            if (mode.name().equalsIgnoreCase(name)) {
                return mode;
            }
        }
        return defaultMode();
    }
}
