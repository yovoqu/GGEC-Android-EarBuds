package com.yovo.lib_ai.models;

import androidx.annotation.NonNull;

/**
 * 模型能做什么。路由按能力选协议和端点，不再按 id 子串猜"这是不是对话模型"。
 *
 * <p>前五个是对话输入/玩法的能力，后三个各自有专用服务，{@link #EMBEDDING} 本轮只占位。
 */
public enum YoModelCapability {
    /** 纯文本对话。 */
    CHAT_TEXT,
    /** 对话输入可以带图片。 */
    CHAT_VISION,
    /** 对话输入可以带音频。 */
    CHAT_AUDIO_IN,
    /** 会产出思考内容。 */
    REASONING,
    /** 支持工具调用（本轮只组装请求，不跑执行循环）。 */
    TOOLS,
    /** 全双工语音。 */
    REALTIME_AUDIO,
    /** 语音转写。 */
    TRANSCRIPTION,
    /** 语音合成。 */
    SPEECH_SYNTHESIS,
    /** 向量化。 */
    EMBEDDING;

    /**
     * 走三条对话协议的能力。
     */
    public boolean conversational() {
        return switch (this) {
            case CHAT_TEXT, CHAT_VISION, CHAT_AUDIO_IN, REASONING, TOOLS -> true;
            case REALTIME_AUDIO, TRANSCRIPTION, SPEECH_SYNTHESIS, EMBEDDING -> false;
        };
    }

    /**
     * 这个能力对应的服务种类；对话能力要先看平台声明了哪条对话协议，所以返回 {@code null}。
     */
    @NonNull
    public YoServiceKind serviceKind() {
        return switch (this) {
            case TRANSCRIPTION -> YoServiceKind.AUDIO_TRANSCRIBE;
            case SPEECH_SYNTHESIS -> YoServiceKind.AUDIO_SPEECH;
            case REALTIME_AUDIO -> YoServiceKind.REALTIME;
            case CHAT_TEXT, CHAT_VISION, CHAT_AUDIO_IN, REASONING, TOOLS, EMBEDDING ->
                    throw new IllegalStateException("capability has no single service kind: " + name());
        };
    }

    /**
     * 拼错误提示用的人话名。{@code UNSUPPORTED_CAPABILITY} 的说明里必须带上它，
     * 否则界面只能显示"请求失败"。
     */
    @NonNull
    public String label() {
        return switch (this) {
            case CHAT_TEXT -> "文本对话";
            case CHAT_VISION -> "看图对话";
            case CHAT_AUDIO_IN -> "语音输入对话";
            case REASONING -> "推理";
            case TOOLS -> "工具调用";
            case REALTIME_AUDIO -> "实时语音";
            case TRANSCRIPTION -> "语音转写";
            case SPEECH_SYNTHESIS -> "语音合成";
            case EMBEDDING -> "向量化";
        };
    }
}
