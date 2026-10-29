package com.yovo.lib_ai.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.Set;

/**
 * 能力推断的第 2 级来源：内置表没命中时按 id 词元猜，一律 {@link YoCapabilitySource#INFERRED}。
 *
 * <p>这段逻辑接管了原来塞在 {@link YoModelKinds#kindOf(String)} 里的"按名字猜用途"，
 * 但两个类各管一件事：
 * <ul>
 *   <li>{@link YoModelKind} 是旧的用途分类（对话/图片/向量/音频…），给模型列表做筛选用，语义保持不变；</li>
 *   <li>{@link YoModelCapability} 是新的能力模型，给路由用。</li>
 * </ul>
 * 认不出的 id 一律当 {@link YoModelCapability#CHAT_TEXT}——宁可多列一个，也不把自定义模型藏起来。
 */
public final class YoModelInference {

    private static final Set<String> EMBEDDING_TOKENS = Set.of("embed", "embedding", "embeddings");
    private static final Set<String> SYNTHESIS_TOKENS = Set.of("tts", "speech", "speechsynthesis");
    private static final Set<String> TRANSCRIPTION_TOKENS = Set.of(
            "whisper", "transcribe", "transcription", "transcriptions", "asr", "stt");
    private static final Set<String> REALTIME_TOKENS = Set.of("realtime");

    private YoModelInference() {
    }

    /**
     * 推断一个模型的能力。永不返回 {@code null}，来源永远是 {@code INFERRED}。
     */
    @NonNull
    public static YoModelDescriptor infer(@Nullable String id) {
        String requested = id == null ? "" : id.trim();
        if (requested.isEmpty()) {
            throw new IllegalArgumentException("model id is required");
        }
        String lower = requested.toLowerCase(Locale.ROOT);
        String[] tokens = lower.split("[^a-z0-9]+");

        if (hasToken(tokens, REALTIME_TOKENS)) {
            return new YoModelDescriptor(requested, requested,
                    Set.of(YoModelCapability.REALTIME_AUDIO), YoCapabilitySource.INFERRED, null, "realtime");
        }
        if (hasToken(tokens, TRANSCRIPTION_TOKENS)) {
            return new YoModelDescriptor(requested, requested,
                    Set.of(YoModelCapability.TRANSCRIPTION), YoCapabilitySource.INFERRED, null, "whisper");
        }
        if (hasToken(tokens, SYNTHESIS_TOKENS)) {
            return new YoModelDescriptor(requested, requested,
                    Set.of(YoModelCapability.SPEECH_SYNTHESIS), YoCapabilitySource.INFERRED, null, "tts");
        }
        if (hasToken(tokens, EMBEDDING_TOKENS)) {
            return new YoModelDescriptor(requested, requested,
                    Set.of(YoModelCapability.EMBEDDING), YoCapabilitySource.INFERRED, null, "embedding");
        }
        Set<YoModelCapability> capabilities = looksLikeReasoning(lower)
                ? Set.of(YoModelCapability.CHAT_TEXT, YoModelCapability.REASONING)
                : Set.of(YoModelCapability.CHAT_TEXT);
        return new YoModelDescriptor(requested, requested, capabilities, YoCapabilitySource.INFERRED,
                null, familyOf(tokens));
    }

    /**
     * 旧接口 {@link YoModelKinds#looksLikeReasoning(String)} 的同义方法，转发过去，规则只有一份。
     */
    public static boolean looksLikeReasoning(@NonNull String id) {
        return YoModelKinds.looksLikeReasoning(id);
    }

    /**
     * 家族推断只用于展示和分组，不参与路由：取 id 的第一个词元。
     */
    @Nullable
    private static String familyOf(@NonNull String[] tokens) {
        return tokens.length == 0 || tokens[0].isEmpty() ? null : tokens[0];
    }

    private static boolean hasToken(@NonNull String[] tokens, @NonNull Set<String> wanted) {
        for (String token : tokens) {
            if (wanted.contains(token)) {
                return true;
            }
        }
        return false;
    }
}
