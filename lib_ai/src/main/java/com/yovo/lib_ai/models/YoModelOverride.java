package com.yovo.lib_ai.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.models.spec.YoModality;
import com.yovo.lib_ai.models.spec.YoModelCapability;
import com.yovo.lib_ai.models.spec.YoModelCategory;
import com.yovo.lib_ai.models.spec.YoModelLimits;
import com.yovo.lib_ai.models.spec.YoModelSpec;
import com.yovo.lib_ai.models.spec.YoReasoning;
import com.yovo.lib_ai.models.spec.YoReasoningMode;
import com.yovo.lib_ai.models.spec.YoSpecSource;
import com.yovo.lib_ai.models.spec.YoSupport;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 用户对某个模型的明确表态。设置页的每个勾选框对应一条：勾上是支持，取消是明确不支持，
 * 没碰过的不在里面——"没表态"和"表态为不支持"必须分开，否则用户永远没法纠正平台或推断的错误。
 *
 * <p>所有说法的出处都是 {@link YoSpecSource#USER}，永久压过推断和平台上报。
 * 不可变；用 {@link #builder()} 构造。</p>
 */
public final class YoModelOverride {

    @Nullable
    private final YoModelCategory category;
    private final Map<YoModality, Boolean> inputs;
    private final Map<YoModality, Boolean> outputs;
    private final Map<YoModelCapability, Boolean> capabilities;
    @Nullable
    private final YoReasoningMode reasoningMode;
    private final YoModelLimits limits;

    private YoModelOverride(Builder builder) {
        this.category = builder.category;
        this.inputs = immutable(builder.inputs);
        this.outputs = immutable(builder.outputs);
        this.capabilities = immutable(builder.capabilities);
        this.reasoningMode = builder.reasoningMode;
        this.limits = builder.limits;
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    /** 用户指定的类别；没指定为 {@code null}。 */
    @Nullable
    public YoModelCategory category() {
        return category;
    }

    /** 用户明确勾上（true）或取消（false）的输入模态。 */
    @NonNull
    public Map<YoModality, Boolean> inputs() {
        return inputs;
    }

    @NonNull
    public Map<YoModality, Boolean> outputs() {
        return outputs;
    }

    @NonNull
    public Map<YoModelCapability, Boolean> capabilities() {
        return capabilities;
    }

    /** 用户指定的推理方式；没指定为 {@code null}。 */
    @Nullable
    public YoReasoningMode reasoningMode() {
        return reasoningMode;
    }

    /** 用户填的数值。 */
    @NonNull
    public YoModelLimits limits() {
        return limits;
    }

    /** 什么都没表过态。 */
    public boolean isEmpty() {
        return category == null && inputs.isEmpty() && outputs.isEmpty() && capabilities.isEmpty()
                && reasoningMode == null && limits.isEmpty();
    }

    /**
     * 把用户的表态盖到 {@code base} 上，得到最终规格。
     */
    @NonNull
    public YoModelSpec applyTo(@NonNull YoModelSpec base) {
        YoModelSpec.Builder spec = base.toBuilder();
        if (category != null) {
            spec.category(category, YoSpecSource.USER);
        }
        for (Map.Entry<YoModality, Boolean> entry : inputs.entrySet()) {
            spec.input(entry.getKey(), YoSupport.of(entry.getValue()), YoSpecSource.USER);
        }
        for (Map.Entry<YoModality, Boolean> entry : outputs.entrySet()) {
            spec.output(entry.getKey(), YoSupport.of(entry.getValue()), YoSpecSource.USER);
        }
        for (Map.Entry<YoModelCapability, Boolean> entry : capabilities.entrySet()) {
            spec.capability(entry.getKey(), YoSupport.of(entry.getValue()), YoSpecSource.USER);
        }
        if (reasoningMode != null) {
            // 用户只指定"怎么想"时，平台报过的力度档位和预算仍然有效，不能被一并抹掉。
            YoReasoning reported = base.reasoning();
            spec.reasoning(new YoReasoning(reasoningMode, reported.efforts(),
                    reported.budgetAdjustable(), YoSpecSource.USER));
        }
        spec.limits(limits);
        return spec.build();
    }

    private static <K extends Enum<K>> Map<K, Boolean> immutable(Map<K, Boolean> source) {
        if (source.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new EnumMap<>(source));
    }

    public static final class Builder {
        @Nullable
        private YoModelCategory category;
        private final Map<YoModality, Boolean> inputs = new EnumMap<>(YoModality.class);
        private final Map<YoModality, Boolean> outputs = new EnumMap<>(YoModality.class);
        private final Map<YoModelCapability, Boolean> capabilities = new EnumMap<>(YoModelCapability.class);
        @Nullable
        private YoReasoningMode reasoningMode;
        private YoModelLimits limits = YoModelLimits.NONE;

        private Builder() {
        }

        /**
         * 直接指定类别。会按这个类别的典型模态补全用户没逐项勾过的输入输出。
         */
        @NonNull
        public Builder category(@Nullable YoModelCategory category) {
            this.category = category;
            return this;
        }

        @NonNull
        public Builder input(@NonNull YoModality modality, boolean supported) {
            inputs.put(Objects.requireNonNull(modality, "modality"), supported);
            return this;
        }

        @NonNull
        public Builder output(@NonNull YoModality modality, boolean supported) {
            outputs.put(Objects.requireNonNull(modality, "modality"), supported);
            return this;
        }

        /**
         * 按勾选框一次写完整组输入：在 {@code checked} 里的是支持，其余是明确不支持。
         */
        @NonNull
        public Builder inputs(@NonNull Set<YoModality> checked) {
            for (YoModality modality : YoModality.values()) {
                input(modality, checked.contains(modality));
            }
            return this;
        }

        @NonNull
        public Builder outputs(@NonNull Set<YoModality> checked) {
            for (YoModality modality : YoModality.values()) {
                output(modality, checked.contains(modality));
            }
            return this;
        }

        @NonNull
        public Builder capability(@NonNull YoModelCapability capability, boolean supported) {
            capabilities.put(Objects.requireNonNull(capability, "capability"), supported);
            return this;
        }

        /**
         * 按勾选框一次写完整组服务端能力：勾上的是支持，其余是明确不支持。
         * 连"一个都不勾"也算数，它表示用户明确说这些都不支持。
         */
        @NonNull
        public Builder capabilities(@NonNull Set<YoModelCapability> checked) {
            for (YoModelCapability capability : YoModelCapability.values()) {
                capability(capability, checked.contains(capability));
            }
            return this;
        }

        @NonNull
        public Builder reasoning(@Nullable YoReasoningMode mode) {
            this.reasoningMode = mode == YoReasoningMode.UNKNOWN ? null : mode;
            return this;
        }

        @NonNull
        public Builder limits(@Nullable YoModelLimits limits) {
            this.limits = limits == null ? YoModelLimits.NONE : limits;
            return this;
        }

        @NonNull
        public YoModelOverride build() {
            return new YoModelOverride(this);
        }
    }
}
