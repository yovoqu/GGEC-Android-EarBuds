package com.ggec.earbuds.domain.ai.model;

import androidx.annotation.Nullable;

/**
 * 与 SDK {@code YoModelCategory} 一一对应的类别。标识和 {@code conversational} 与 SDK 相同，
 * 只在网关从 {@code YoModelSpec.category()} 抄过来，领域不再把其余类别收成「普通」。
 */
public enum AiModelCategory {

    TEXT("text", true),
    VISION("vision", true),
    OMNI("omni", true),
    SPEECH_RECOGNITION("speech_recognition", false),
    SPEECH_SYNTHESIS("speech_synthesis", false),
    EMBEDDING("embedding", false),
    REALTIME("realtime", false),
    IMAGE_GENERATION("image_generation", false),
    VIDEO_GENERATION("video_generation", false),
    RERANK("rerank", false),
    MODERATION("moderation", false),
    OTHER("other", false);

    private final String id;
    private final boolean conversational;

    AiModelCategory(String id, boolean conversational) {
        this.id = id;
        this.conversational = conversational;
    }

    public String id() {
        return id;
    }

    /** 走对话协议的三类：文本、视觉、全模态。与 SDK {@code conversational()} 一致。 */
    public boolean conversational() {
        return conversational;
    }

    @Nullable
    public static AiModelCategory fromId(@Nullable String id) {
        if (id == null) {
            return null;
        }
        for (AiModelCategory category : values()) {
            if (category.id.equals(id)) {
                return category;
            }
        }
        return null;
    }
}
