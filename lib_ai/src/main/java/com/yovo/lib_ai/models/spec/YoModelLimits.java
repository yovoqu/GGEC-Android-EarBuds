package com.yovo.lib_ai.models.spec;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * 模型的体量上限。每一项都可能没人说过，没说过就是 {@code null}——平台没给的数，SDK 不凭 id 猜。
 *
 * <p>几个长度的关系要分清，它们不是同一个数：
 * <ul>
 *   <li>{@code contextWindow}：上下文窗口，<b>输入和输出共用</b>的总长度；</li>
 *   <li>{@code maxInputTokens}：单次请求最多能送多少输入，可以比窗口小（窗口里要给输出留位置）；</li>
 *   <li>{@code maxOutputTokens}：单次最多能生成多少，可以比窗口小得多；</li>
 *   <li>{@code maxReasoningTokens}：思维链单独的上限，推理模型的"想"和"答"往往分开计。</li>
 * </ul>
 * 实际能放多少用 {@link #inputCapacity()}、{@link #outputCapacity(int)} 算，不要自己拿两个数比大小。</p>
 *
 * <p>这里没有出处：数值只可能来自平台上报或用户填写，都可信；推断从不产出数值。
 * 几层来源依次叠加时，后写的非空项覆盖先写的。</p>
 *
 * @param contextWindow       上下文窗口（输入 + 输出）
 * @param maxInputTokens      单次输入上限
 * @param maxOutputTokens     单次输出上限
 * @param maxReasoningTokens  思维链上限
 * @param embeddingDimensions 向量维度（向量模型的输出长度）
 * @param inputMedia          各输入模态的体量上限，没有的模态不在表里
 */
public record YoModelLimits(
        @Nullable Integer contextWindow,
        @Nullable Integer maxInputTokens,
        @Nullable Integer maxOutputTokens,
        @Nullable Integer maxReasoningTokens,
        @Nullable Integer embeddingDimensions,
        @NonNull Map<YoModality, YoMediaLimit> inputMedia) {

    /** 一项都没说过。 */
    public static final YoModelLimits NONE = new YoModelLimits(null, null, null, null, null, Map.of());

    public YoModelLimits {
        contextWindow = positive(contextWindow);
        maxInputTokens = positive(maxInputTokens);
        maxOutputTokens = positive(maxOutputTokens);
        maxReasoningTokens = positive(maxReasoningTokens);
        embeddingDimensions = positive(embeddingDimensions);
        inputMedia = immutableMedia(inputMedia);
    }

    /**
     * 最常用的构造：只有三个 token 数。
     */
    @NonNull
    public static YoModelLimits tokens(@Nullable Integer contextWindow,
                                       @Nullable Integer maxInputTokens,
                                       @Nullable Integer maxOutputTokens) {
        return new YoModelLimits(contextWindow, maxInputTokens, maxOutputTokens, null, null, Map.of());
    }

    @NonNull
    public static Builder builder() {
        return new Builder(NONE);
    }

    @NonNull
    public Builder toBuilder() {
        return new Builder(this);
    }

    public boolean isEmpty() {
        return contextWindow == null && maxInputTokens == null && maxOutputTokens == null
                && maxReasoningTokens == null && embeddingDimensions == null && inputMedia.isEmpty();
    }

    /**
     * 某个输入模态的体量上限；没人说过返回 {@code null}。
     */
    @Nullable
    public YoMediaLimit media(@NonNull YoModality modality) {
        return inputMedia.get(modality);
    }

    /**
     * 单次请求最多能送多少输入 token：输入上限和窗口里更紧的那个；两个都没说过返回 {@code null}。
     */
    @Nullable
    public Integer inputCapacity() {
        if (maxInputTokens != null && contextWindow != null) {
            return Math.min(maxInputTokens, contextWindow);
        }
        return maxInputTokens != null ? maxInputTokens : contextWindow;
    }

    /**
     * 已经送进去 {@code inputTokens} 个输入之后，最多还能要多少输出：
     * 输出上限和"窗口里剩下的位置"里更紧的那个；两个都没说过返回 {@code null}。
     * 输入已经把窗口占满时返回 0，不返回负数。
     */
    @Nullable
    public Integer outputCapacity(int inputTokens) {
        Integer room = contextWindow == null ? null : Math.max(0, contextWindow - Math.max(0, inputTokens));
        if (maxOutputTokens == null) {
            return room;
        }
        return room == null ? maxOutputTokens : Math.min(maxOutputTokens, room);
    }

    /**
     * {@code later} 里说过的覆盖这里的，没说的保留；媒体上限按模态逐项合并。
     */
    @NonNull
    public YoModelLimits overlay(@Nullable YoModelLimits later) {
        if (later == null || later.isEmpty()) {
            return this;
        }
        Builder merged = toBuilder();
        if (later.contextWindow != null) {
            merged.contextWindow(later.contextWindow);
        }
        if (later.maxInputTokens != null) {
            merged.maxInputTokens(later.maxInputTokens);
        }
        if (later.maxOutputTokens != null) {
            merged.maxOutputTokens(later.maxOutputTokens);
        }
        if (later.maxReasoningTokens != null) {
            merged.maxReasoningTokens(later.maxReasoningTokens);
        }
        if (later.embeddingDimensions != null) {
            merged.embeddingDimensions(later.embeddingDimensions);
        }
        for (Map.Entry<YoModality, YoMediaLimit> entry : later.inputMedia.entrySet()) {
            YoMediaLimit current = merged.media.get(entry.getKey());
            merged.media.put(entry.getKey(),
                    current == null ? entry.getValue() : current.overlay(entry.getValue()));
        }
        return merged.build();
    }

    @Nullable
    private static Integer positive(@Nullable Integer value) {
        return value != null && value > 0 ? value : null;
    }

    @NonNull
    private static Map<YoModality, YoMediaLimit> immutableMedia(@Nullable Map<YoModality, YoMediaLimit> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        EnumMap<YoModality, YoMediaLimit> copy = new EnumMap<>(YoModality.class);
        for (Map.Entry<YoModality, YoMediaLimit> entry : source.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null && !entry.getValue().isEmpty()) {
                copy.put(entry.getKey(), entry.getValue());
            }
        }
        return copy.isEmpty() ? Map.of() : Collections.unmodifiableMap(copy);
    }

    public static final class Builder {
        @Nullable
        private Integer contextWindow;
        @Nullable
        private Integer maxInputTokens;
        @Nullable
        private Integer maxOutputTokens;
        @Nullable
        private Integer maxReasoningTokens;
        @Nullable
        private Integer embeddingDimensions;
        private final EnumMap<YoModality, YoMediaLimit> media = new EnumMap<>(YoModality.class);

        private Builder(YoModelLimits from) {
            this.contextWindow = from.contextWindow;
            this.maxInputTokens = from.maxInputTokens;
            this.maxOutputTokens = from.maxOutputTokens;
            this.maxReasoningTokens = from.maxReasoningTokens;
            this.embeddingDimensions = from.embeddingDimensions;
            this.media.putAll(from.inputMedia);
        }

        @NonNull
        public Builder contextWindow(@Nullable Integer contextWindow) {
            this.contextWindow = contextWindow;
            return this;
        }

        @NonNull
        public Builder maxInputTokens(@Nullable Integer maxInputTokens) {
            this.maxInputTokens = maxInputTokens;
            return this;
        }

        @NonNull
        public Builder maxOutputTokens(@Nullable Integer maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
            return this;
        }

        @NonNull
        public Builder maxReasoningTokens(@Nullable Integer maxReasoningTokens) {
            this.maxReasoningTokens = maxReasoningTokens;
            return this;
        }

        @NonNull
        public Builder embeddingDimensions(@Nullable Integer embeddingDimensions) {
            this.embeddingDimensions = embeddingDimensions;
            return this;
        }

        @NonNull
        public Builder media(@NonNull YoModality modality, @Nullable YoMediaLimit limit) {
            if (limit == null || limit.isEmpty()) {
                media.remove(modality);
            } else {
                media.put(modality, limit);
            }
            return this;
        }

        @NonNull
        public YoModelLimits build() {
            return new YoModelLimits(contextWindow, maxInputTokens, maxOutputTokens,
                    maxReasoningTokens, embeddingDimensions, media);
        }
    }
}
