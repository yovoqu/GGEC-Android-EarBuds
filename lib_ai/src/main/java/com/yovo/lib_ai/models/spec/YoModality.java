package com.yovo.lib_ai.models.spec;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * 模型能收什么、产出什么。输入和输出各自是一组模态。
 *
 * <p>{@link #VECTOR} 只会出现在输出侧（向量模型的产物），{@link #DOCUMENT} 主要出现在输入侧（PDF 等文件）。</p>
 */
public enum YoModality {
    TEXT("文本"),
    IMAGE("图片"),
    AUDIO("音频"),
    VIDEO("视频"),
    DOCUMENT("文档"),
    VECTOR("向量");

    private final String label;

    YoModality(String label) {
        this.label = label;
    }

    /**
     * 拼错误提示和界面标签用的人话名。
     */
    @NonNull
    public String label() {
        return label;
    }

    /**
     * 宽松解析平台写的模态名（{@code text}、{@code image}、{@code pdf}、{@code file}……）。
     * 认不出的返回 {@code null}，调用方直接忽略，不要为生僻写法抛异常。
     */
    @Nullable
    public static YoModality fromName(@Nullable String name) {
        if (name == null) {
            return null;
        }
        return switch (name.trim().toLowerCase(Locale.ROOT)) {
            case "text", "string" -> TEXT;
            case "image", "images", "img", "picture", "vision" -> IMAGE;
            case "audio", "speech", "voice", "sound" -> AUDIO;
            case "video", "videos" -> VIDEO;
            case "file", "files", "pdf", "document", "documents", "doc" -> DOCUMENT;
            case "embedding", "embeddings", "vector", "vectors" -> VECTOR;
            default -> null;
        };
    }
}
