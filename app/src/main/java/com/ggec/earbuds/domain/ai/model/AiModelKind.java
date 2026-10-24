package com.ggec.earbuds.domain.ai.model;

/**
 * 模型类型。只有 {@link #CHAT} 能设为当前模型、参与测速和生成参数；其余类型只在列表里展示与删除。
 */
public enum AiModelKind {
    CHAT,
    EMBEDDING,
    IMAGE,
    AUDIO,
    MODERATION,
    OTHER;

    public boolean chat() {
        return this == CHAT;
    }
}
