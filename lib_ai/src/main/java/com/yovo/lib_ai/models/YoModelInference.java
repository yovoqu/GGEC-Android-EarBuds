package com.yovo.lib_ai.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.models.spec.YoModelCategory;
import com.yovo.lib_ai.models.spec.YoModelSpec;
import com.yovo.lib_ai.models.spec.YoReasoning;
import com.yovo.lib_ai.models.spec.YoReasoningMode;
import com.yovo.lib_ai.models.spec.YoSpecSource;

import java.util.Locale;
import java.util.Set;

/**
 * 规格的最底层来源：只凭 id 的词元猜，一律 {@link YoSpecSource#INFERRED}。分类词表整个 SDK 只有这一份。
 *
 * <p>猜的范围很窄，只回答两个问题：它大概是哪一类服务（实时、语音、向量、图像……），
 * 以及名字里有没有推理的字样。<b>不猜</b>能不能看图、能不能听音、上下文多长、有没有函数调用——
 * 猜错的代价是"看起来支持、实际 400"，这些交给平台上报或用户确认。所以对话类的细分
 * （文本 / 视觉 / 全模态）在这里一律是文本，升档要靠可信的模态事实，见 {@link YoModelSpec}。</p>
 *
 * <p>认不出的 id 一律当文本模型——宁可多列一个，也不把自定义模型藏起来。</p>
 *
 * <p>类别按固定优先级判断，而不是谁的词元先出现谁说了算：{@code omni-moderation-latest} 是审核模型，
 * {@code qwen3-omni-flash-realtime} 是实时模型，后缀比前缀更能说明用途。</p>
 */
public final class YoModelInference {

    private static final Set<String> REALTIME = Set.of("realtime");
    private static final Set<String> MODERATION = Set.of("moderation", "moderations");
    private static final Set<String> RECOGNITION = Set.of(
            "whisper", "transcribe", "transcription", "transcriptions", "asr", "stt");
    private static final Set<String> SYNTHESIS = Set.of("tts", "speech", "speechsynthesis");
    private static final Set<String> RERANK = Set.of("rerank", "reranker");
    private static final Set<String> EMBEDDING = Set.of("embed", "embedding", "embeddings", "bge");
    private static final Set<String> IMAGE = Set.of(
            "dall", "imagen", "diffusion", "flux", "midjourney", "cogview", "wanx", "image");
    private static final Set<String> VIDEO = Set.of("sora", "veo", "kling", "cogvideo");
    private static final Set<String> LEGACY_COMPLETION = Set.of("davinci", "babbage", "curie");
    private static final Set<String> INSTRUCT = Set.of("instruct");
    private static final Set<String> TURBO = Set.of("turbo");
    private static final Set<String> REASONING = Set.of(
            "reasoner", "reasoning", "thinking", "r1", "qwq", "o1", "o3", "o4");

    private YoModelInference() {
    }

    /**
     * 推断一个模型的规格。永不返回 {@code null}，所有说法的出处都是 {@code INFERRED}。
     *
     * @throws IllegalArgumentException id 为空
     */
    @NonNull
    public static YoModelSpec infer(@Nullable String id) {
        String requested = id == null ? "" : id.trim();
        if (requested.isEmpty()) {
            throw new IllegalArgumentException("model id is required");
        }
        YoModelCategory category = categoryOf(requested);
        YoModelSpec.Builder spec = YoModelSpec.builder(requested)
                .category(category, YoSpecSource.INFERRED);
        if (category.conversational() && looksLikeReasoning(requested)) {
            spec.reasoning(YoReasoning.of(YoReasoningMode.SUPPORTED, YoSpecSource.INFERRED));
        }
        return spec.build();
    }

    /**
     * 按 id 判断类别。认不出的是 {@link YoModelCategory#TEXT}。
     */
    @NonNull
    public static YoModelCategory categoryOf(@NonNull String id) {
        String lower = id.toLowerCase(Locale.ROOT);
        String[] tokens = lower.split("[^a-z0-9]+");
        if (hasToken(tokens, REALTIME)) {
            return YoModelCategory.REALTIME;
        }
        if (hasToken(tokens, MODERATION)) {
            return YoModelCategory.MODERATION;
        }
        if (hasToken(tokens, RECOGNITION)) {
            return YoModelCategory.SPEECH_RECOGNITION;
        }
        if (hasToken(tokens, SYNTHESIS)) {
            return YoModelCategory.SPEECH_SYNTHESIS;
        }
        if (hasToken(tokens, RERANK)) {
            return YoModelCategory.RERANK;
        }
        if (hasToken(tokens, EMBEDDING)) {
            return YoModelCategory.EMBEDDING;
        }
        if (lower.startsWith("sensenova-u1") || lower.contains("/sensenova-u1") || hasToken(tokens, IMAGE)) {
            return YoModelCategory.IMAGE_GENERATION;
        }
        if (hasToken(tokens, VIDEO)) {
            return YoModelCategory.VIDEO_GENERATION;
        }
        // "instruct" 单独出现是大量对话模型的后缀（xxx-Instruct），只有 turbo-instruct 才是旧的文本补全模型。
        if (hasToken(tokens, LEGACY_COMPLETION) || (hasToken(tokens, INSTRUCT) && hasToken(tokens, TURBO))) {
            return YoModelCategory.OTHER;
        }
        return YoModelCategory.TEXT;
    }

    /**
     * 名字里有没有推理的字样。只用来给没有能力元数据的平台补一个"可能支持推理"的提示。
     */
    public static boolean looksLikeReasoning(@NonNull String id) {
        return hasToken(id.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"), REASONING);
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
