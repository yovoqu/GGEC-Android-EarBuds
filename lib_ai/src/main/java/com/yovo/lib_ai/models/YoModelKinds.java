package com.yovo.lib_ai.models;

import androidx.annotation.NonNull;

import java.util.Locale;
import java.util.Set;

/**
 * 平台模型列表通常不区分对话与非对话模型，这里按 id 的词元做保守分类。
 * 认不出的 id 一律当对话模型，宁可多列一个也不把自定义模型藏起来。
 */
public final class YoModelKinds {

    private static final Set<String> EMBEDDING = Set.of(
            "embedding", "embeddings", "embed", "bge", "rerank", "reranker");
    private static final Set<String> IMAGE = Set.of(
            "dall", "imagen", "diffusion", "flux", "midjourney", "cogview", "wanx", "image");
    private static final Set<String> AUDIO = Set.of(
            "whisper", "tts", "asr", "transcribe", "speech");
    private static final Set<String> MODERATION = Set.of("moderation", "moderations");
    private static final Set<String> OTHER = Set.of(
            "davinci", "babbage", "curie", "instruct", "realtime", "sora", "veo", "kling", "cogvideo");
    private static final Set<String> REASONING = Set.of(
            "reasoner", "reasoning", "thinking", "r1", "qwq", "o1", "o3", "o4");

    private YoModelKinds() {
    }

    @NonNull
    public static YoModelKind kindOf(@NonNull String id) {
        String lower = id.toLowerCase(Locale.ROOT);
        if (lower.startsWith("sensenova-u1") || lower.contains("/sensenova-u1")) {
            return YoModelKind.IMAGE;
        }
        String[] tokens = lower.split("[^a-z0-9]+");
        for (String token : tokens) {
            if (EMBEDDING.contains(token)) {
                return YoModelKind.EMBEDDING;
            }
            if (MODERATION.contains(token)) {
                return YoModelKind.MODERATION;
            }
            if (AUDIO.contains(token)) {
                return YoModelKind.AUDIO;
            }
            if (IMAGE.contains(token)) {
                return YoModelKind.IMAGE;
            }
            if (OTHER.contains(token)) {
                return YoModelKind.OTHER;
            }
        }
        return YoModelKind.CHAT;
    }

    /**
     * 只用来给没有能力元数据的平台补一个"可能支持推理"的提示。
     */
    public static boolean looksLikeReasoning(@NonNull String id) {
        for (String token : id.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (REASONING.contains(token)) {
                return true;
            }
        }
        return false;
    }
}
