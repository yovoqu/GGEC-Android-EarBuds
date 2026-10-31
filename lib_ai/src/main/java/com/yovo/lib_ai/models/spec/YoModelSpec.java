package com.yovo.lib_ai.models.spec;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * 一个模型的完整规格：它是哪一类、收什么产出什么、体量多大、有哪些服务端能力、会不会推理，
 * 以及每一条说法是谁给的。
 *
 * <pre>
 *   类别 category        文本 / 视觉 / 全模态 / 语音识别 / 语音合成 / 向量 / 实时 / ……
 *   模态 inputs·outputs  文本 图片 音频 视频 文档 向量
 *   限额 limits          上下文窗口 · 输入 · 输出 · 思维链 · 向量维度 · 各模态体量
 *   能力 capabilities    函数调用 结构化输出 联网搜索 前缀续写 上下文缓存 批量推理
 *   推理 reasoning       怎么想 · 力度档位 · 预算
 * </pre>
 *
 * <p>这几根轴互相独立，不再像旧版那样挤在一个枚举里。"全模态"不是一个特殊能力位，
 * 而是 {@link YoModelCategory#OMNI} 加上一组实际生效的模态。</p>
 *
 * <p><b>三层来源，高覆盖低</b>：按 id 词元推断（{@link YoSpecSource#INFERRED}）→ 平台模型列表上报
 * （{@link YoSpecSource#REPORTED}）→ 用户指定（{@link YoSpecSource#USER}）。每个模态、每项能力、
 * 类别、推理各自带出处，所以"平台只报了上下文长度"不会连带把别的项也变成可信。
 * 没有人表过态的项是 {@link YoSupport#UNKNOWN}，它不等于不支持。</p>
 *
 * <p><b>类别是推出来的，不是另存的</b>：对话类的细分（文本 / 视觉 / 全模态）由可信的模态事实决定——
 * 平台报了能收图片就是视觉，报了音频就是全模态，名字上看起来像并不算数。只有用户可以直接指定类别。
 * 因为是现算的，后来的来源改了模态，类别会跟着变，不会留下过期的结论。</p>
 *
 * <p>不可变；用 {@link #toBuilder()} 叠加下一层来源。</p>
 */
public final class YoModelSpec {

    private final String id;
    private final String displayName;
    private final YoModelCategory declaredCategory;
    private final YoSpecSource declaredSource;
    private final YoSupportMap<YoModality> inputs;
    private final YoSupportMap<YoModality> outputs;
    private final YoSupportMap<YoModelCapability> capabilities;
    private final YoModelLimits limits;
    private final YoReasoning reasoning;
    private final YoModelCategory category;
    private final YoSpecSource categorySource;

    private YoModelSpec(Builder builder) {
        this.id = builder.id;
        this.displayName = builder.displayName == null ? builder.id : builder.displayName;
        this.declaredCategory = builder.category;
        this.declaredSource = builder.categorySource;
        this.inputs = builder.inputs;
        this.outputs = builder.outputs;
        this.capabilities = builder.capabilities;
        this.limits = builder.limits;
        this.reasoning = builder.reasoning;

        YoModelCategory resolved = declaredCategory;
        YoSpecSource resolvedSource = declaredSource;
        if (declaredSource != YoSpecSource.USER && declaredCategory.conversational()) {
            YoSpecSource omni = strongest(
                    inputs.fact(YoModality.AUDIO),
                    outputs.fact(YoModality.AUDIO), outputs.fact(YoModality.IMAGE), outputs.fact(YoModality.VIDEO));
            YoSpecSource visual = strongest(inputs.fact(YoModality.IMAGE), inputs.fact(YoModality.VIDEO));
            YoModelCategory fromEvidence = omni != null ? YoModelCategory.OMNI
                    : visual != null ? YoModelCategory.VISION : YoModelCategory.TEXT;
            // 只升不降：证据没覆盖到的部分，保留已有的声明。
            if (fromEvidence != declaredCategory && fromEvidence.canServe(declaredCategory)) {
                resolved = fromEvidence;
                resolvedSource = omni != null ? omni : visual;
            }
        }
        this.category = resolved;
        this.categorySource = resolvedSource;
    }

    /**
     * 这些事实里可信的、明确说支持的那几条里最靠谱的出处；一条都没有返回 {@code null}。
     */
    @Nullable
    private static YoSpecSource strongest(YoFact... facts) {
        YoSpecSource best = null;
        for (YoFact fact : facts) {
            if (fact.confirmed()) {
                best = best == null ? fact.source() : YoSpecSource.stronger(best, fact.source());
            }
        }
        return best;
    }

    @NonNull
    public static Builder builder(@NonNull String id) {
        return new Builder(id);
    }

    /**
     * 以当前规格为底，叠加下一层来源的说法。
     */
    @NonNull
    public Builder toBuilder() {
        Builder builder = new Builder(id);
        builder.displayName = displayName.equals(id) ? null : displayName;
        builder.category = declaredCategory;
        builder.categorySource = declaredSource;
        builder.inputs = inputs;
        builder.outputs = outputs;
        builder.capabilities = capabilities;
        builder.limits = limits;
        builder.reasoning = reasoning;
        return builder;
    }

    /** 对话请求里的 {@code model} 字段原文。 */
    @NonNull
    public String id() {
        return id;
    }

    /** 界面展示名，平台没给时等于 id。 */
    @NonNull
    public String displayName() {
        return displayName;
    }

    /**
     * 模型的类别。对话类按可信的模态事实细分，见类注释。
     */
    @NonNull
    public YoModelCategory category() {
        return category;
    }

    /**
     * {@link #category()} 这个结论是谁给的。对话类升档时，出处是促成升档的那条模态事实的出处。
     */
    @NonNull
    public YoSpecSource categorySource() {
        return categorySource;
    }

    /** 走三条对话协议。 */
    public boolean conversational() {
        return category.conversational();
    }

    // ---- 模态 ----

    /**
     * 能不能收这种输入，以及这话是谁说的。没有人明确表过态时，按类别的典型模态给一条推断级的答复
     * （例如文本模型默认收文本）；用户直接指定类别时，这条答复算用户的。
     */
    @NonNull
    public YoFact input(@NonNull YoModality modality) {
        return modalityFact(inputs, modality, category.typicalInputs());
    }

    /** 能不能产出这种输出，口径同 {@link #input}。 */
    @NonNull
    public YoFact output(@NonNull YoModality modality) {
        return modalityFact(outputs, modality, category.typicalOutputs());
    }

    @NonNull
    private YoFact modalityFact(YoSupportMap<YoModality> explicit, YoModality modality,
                                Set<YoModality> typical) {
        YoFact fact = explicit.fact(modality);
        if (fact.known()) {
            return fact;
        }
        if (typical.contains(modality)) {
            return new YoFact(YoSupport.YES,
                    declaredSource == YoSpecSource.USER ? YoSpecSource.USER : YoSpecSource.INFERRED);
        }
        return YoFact.UNKNOWN;
    }

    public boolean accepts(@NonNull YoModality modality) {
        return input(modality).yes();
    }

    public boolean produces(@NonNull YoModality modality) {
        return output(modality).yes();
    }

    /** 全部明确（或按类别典型推断）能收的输入。 */
    @NonNull
    public Set<YoModality> inputs() {
        return modalities(true);
    }

    /** 全部明确（或按类别典型推断）能产出的输出。 */
    @NonNull
    public Set<YoModality> outputs() {
        return modalities(false);
    }

    @NonNull
    private Set<YoModality> modalities(boolean input) {
        EnumSet<YoModality> set = EnumSet.noneOf(YoModality.class);
        for (YoModality modality : YoModality.values()) {
            if (input ? accepts(modality) : produces(modality)) {
                set.add(modality);
            }
        }
        return Collections.unmodifiableSet(set);
    }

    // ---- 能力 ----

    @NonNull
    public YoFact capability(@NonNull YoModelCapability capability) {
        return capabilities.fact(capability);
    }

    public boolean supports(@NonNull YoModelCapability capability) {
        return capabilities.has(capability);
    }

    /** 全部明确支持的服务端能力。 */
    @NonNull
    public Set<YoModelCapability> capabilities() {
        return capabilities.yes();
    }

    // ---- 限额与推理 ----

    @NonNull
    public YoModelLimits limits() {
        return limits;
    }

    @NonNull
    public YoReasoning reasoning() {
        return reasoning;
    }

    /** 会产出思考内容（不论出处）。 */
    public boolean reasons() {
        return reasoning.reasons();
    }

    // ---- 对象约定 ----

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof YoModelSpec spec)) {
            return false;
        }
        return id.equals(spec.id)
                && displayName.equals(spec.displayName)
                && declaredCategory == spec.declaredCategory
                && declaredSource == spec.declaredSource
                && inputs.equals(spec.inputs)
                && outputs.equals(spec.outputs)
                && capabilities.equals(spec.capabilities)
                && limits.equals(spec.limits)
                && reasoning.equals(spec.reasoning);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, displayName, declaredCategory, declaredSource,
                inputs, outputs, capabilities, limits, reasoning);
    }

    @NonNull
    @Override
    public String toString() {
        return "YoModelSpec{id=" + id
                + ", category=" + category + "(" + categorySource + ")"
                + ", inputs=" + inputs()
                + ", outputs=" + outputs()
                + ", capabilities=" + capabilities()
                + ", reasoning=" + reasoning.mode()
                + '}';
    }

    /**
     * 叠加说法的构造器。每一条说法都带出处，出处低的改不动出处高的结论；同级后写的为准。
     * 所以几层来源只要各自写进来，不必关心先后。
     */
    public static final class Builder {
        private final String id;
        @Nullable
        private String displayName;
        private YoModelCategory category = YoModelCategory.TEXT;
        private YoSpecSource categorySource = YoSpecSource.INFERRED;
        private YoSupportMap<YoModality> inputs = YoSupportMap.empty(YoModality.class);
        private YoSupportMap<YoModality> outputs = YoSupportMap.empty(YoModality.class);
        private YoSupportMap<YoModelCapability> capabilities = YoSupportMap.empty(YoModelCapability.class);
        private YoModelLimits limits = YoModelLimits.NONE;
        private YoReasoning reasoning = YoReasoning.UNKNOWN;

        private Builder(String id) {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("model id is required");
            }
            this.id = id.trim();
        }

        @NonNull
        public Builder displayName(@Nullable String displayName) {
            this.displayName = displayName == null || displayName.isBlank() ? null : displayName.trim();
            return this;
        }

        /**
         * 声明类别。对话类（文本 / 视觉 / 全模态）的细分以可信的模态事实为准，
         * 除非声明来自 {@link YoSpecSource#USER}。
         */
        @NonNull
        public Builder category(@NonNull YoModelCategory category, @NonNull YoSpecSource source) {
            Objects.requireNonNull(category, "category");
            if (source.overrides(categorySource)) {
                this.category = category;
                this.categorySource = source;
            }
            return this;
        }

        @NonNull
        public Builder input(@NonNull YoModality modality, @NonNull YoSupport support,
                             @NonNull YoSpecSource source) {
            this.inputs = inputs.with(modality, support, source);
            return this;
        }

        /**
         * 写一份完整的输入清单：列出的支持，其余全部不支持。仅用于语义上穷举的字段。
         */
        @NonNull
        public Builder inputs(@NonNull Collection<YoModality> listed, @NonNull YoSpecSource source) {
            this.inputs = inputs.withExhaustive(listed, source);
            return this;
        }

        @NonNull
        public Builder output(@NonNull YoModality modality, @NonNull YoSupport support,
                              @NonNull YoSpecSource source) {
            this.outputs = outputs.with(modality, support, source);
            return this;
        }

        @NonNull
        public Builder outputs(@NonNull Collection<YoModality> listed, @NonNull YoSpecSource source) {
            this.outputs = outputs.withExhaustive(listed, source);
            return this;
        }

        @NonNull
        public Builder capability(@NonNull YoModelCapability capability, @NonNull YoSupport support,
                                  @NonNull YoSpecSource source) {
            this.capabilities = capabilities.with(capability, support, source);
            return this;
        }

        /**
         * 叠加限额：说过的项覆盖已有的，没说的保留。数值只会来自平台上报或用户，不分出处档。
         */
        @NonNull
        public Builder limits(@Nullable YoModelLimits limits) {
            this.limits = this.limits.overlay(limits);
            return this;
        }

        /**
         * 写推理面。{@link YoReasoningMode#UNKNOWN} 等于没说；出处低于已有说法时不生效。
         */
        @NonNull
        public Builder reasoning(@NonNull YoReasoning reasoning) {
            Objects.requireNonNull(reasoning, "reasoning");
            if (reasoning.mode() == YoReasoningMode.UNKNOWN) {
                return this;
            }
            if (this.reasoning.mode() == YoReasoningMode.UNKNOWN
                    || reasoning.source().overrides(this.reasoning.source())) {
                this.reasoning = reasoning;
            }
            return this;
        }

        @NonNull
        public YoModelSpec build() {
            return new YoModelSpec(this);
        }
    }
}
