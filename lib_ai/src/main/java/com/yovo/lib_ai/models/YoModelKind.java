package com.yovo.lib_ai.models;

/**
 * 模型的用途分类。只有 {@link #CHAT} 能走三条对话协议。
 */
public enum YoModelKind {
    CHAT,
    EMBEDDING,
    IMAGE,
    AUDIO,
    MODERATION,
    OTHER;

    public boolean chatCapable() {
        return this == CHAT;
    }
}
