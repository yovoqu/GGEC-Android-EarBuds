package com.yovo.lib_ai.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.models.spec.YoFact;
import com.yovo.lib_ai.models.spec.YoModality;
import com.yovo.lib_ai.models.spec.YoModelCapability;
import com.yovo.lib_ai.models.spec.YoModelCategory;
import com.yovo.lib_ai.models.spec.YoModelSpec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 一次调用对模型的要求。路由按它选服务、选模型，不再按"能力"这个混合枚举猜。
 *
 * <p>要求由两部分叠成：
 * <ul>
 *   <li><b>类别</b>（必填）：至少是哪一类。对话三类向下兼容——要文本的，视觉和全模态模型也行；
 *       要视觉的，全模态模型也行；其余类别只认自己（见 {@link YoModelCategory#canServe}）。</li>
 *   <li><b>附加条件</b>（可选）：必须能收或产出的模态、必须具备的服务端能力、必须会推理、
 *       上下文和输出的下限。条件之间是"且"。</li>
 * </ul>
 * 不可变，{@code with…} 方法返回新的要求：
 * <pre>
 *   YoModelRequirement.text()
 *       .withCapability(YoModelCapability.FUNCTION_CALLING)
 *       .withMinContext(128_000);
 * </pre></p>
 *
 * <p>体量条件只拦"明确不够"：规格里没有这项数值时放行，因为平台没说不等于装不下。</p>
 */
public final class YoModelRequirement {

    private final YoModelCategory category;
    private final Set<YoModality> inputs;
    private final Set<YoModality> outputs;
    private final Set<YoModelCapability> capabilities;
    private final boolean reasoning;
    @Nullable
    private final Integer minContextTokens;
    @Nullable
    private final Integer minOutputTokens;

    private YoModelRequirement(YoModelCategory category,
                               Set<YoModality> inputs,
                               Set<YoModality> outputs,
                               Set<YoModelCapability> capabilities,
                               boolean reasoning,
                               @Nullable Integer minContextTokens,
                               @Nullable Integer minOutputTokens) {
        this.category = category;
        this.inputs = Collections.unmodifiableSet(copy(inputs, YoModality.class));
        this.outputs = Collections.unmodifiableSet(copy(outputs, YoModality.class));
        this.capabilities = Collections.unmodifiableSet(copy(capabilities, YoModelCapability.class));
        this.reasoning = reasoning;
        this.minContextTokens = minContextTokens;
        this.minOutputTokens = minOutputTokens;
    }

    @NonNull
    public static YoModelRequirement of(@NonNull YoModelCategory category) {
        return new YoModelRequirement(Objects.requireNonNull(category, "category"),
                Set.of(), Set.of(), Set.of(), false, null, null);
    }

    /** 能做文本对话的模型（文本、视觉、全模态都行）。 */
    @NonNull
    public static YoModelRequirement text() {
        return of(YoModelCategory.TEXT);
    }

    /** 能看图的对话模型（视觉、全模态）。 */
    @NonNull
    public static YoModelRequirement vision() {
        return of(YoModelCategory.VISION);
    }

    /** 全模态对话模型。 */
    @NonNull
    public static YoModelRequirement omni() {
        return of(YoModelCategory.OMNI);
    }

    @NonNull
    public static YoModelRequirement realtime() {
        return of(YoModelCategory.REALTIME);
    }

    @NonNull
    public static YoModelRequirement speechRecognition() {
        return of(YoModelCategory.SPEECH_RECOGNITION);
    }

    @NonNull
    public static YoModelRequirement speechSynthesis() {
        return of(YoModelCategory.SPEECH_SYNTHESIS);
    }

    @NonNull
    public static YoModelRequirement embedding() {
        return of(YoModelCategory.EMBEDDING);
    }

    // ---- 附加条件 ----

    /** 必须能收这些输入模态。 */
    @NonNull
    public YoModelRequirement withInput(@NonNull YoModality... modalities) {
        EnumSet<YoModality> next = copy(inputs, YoModality.class);
        Collections.addAll(next, modalities);
        return new YoModelRequirement(category, next, outputs, capabilities, reasoning,
                minContextTokens, minOutputTokens);
    }

    /** 必须能产出这些输出模态。 */
    @NonNull
    public YoModelRequirement withOutput(@NonNull YoModality... modalities) {
        EnumSet<YoModality> next = copy(outputs, YoModality.class);
        Collections.addAll(next, modalities);
        return new YoModelRequirement(category, inputs, next, capabilities, reasoning,
                minContextTokens, minOutputTokens);
    }

    /** 必须具备这些服务端能力。 */
    @NonNull
    public YoModelRequirement withCapability(@NonNull YoModelCapability... wanted) {
        EnumSet<YoModelCapability> next = copy(capabilities, YoModelCapability.class);
        Collections.addAll(next, wanted);
        return new YoModelRequirement(category, inputs, outputs, next, reasoning,
                minContextTokens, minOutputTokens);
    }

    /** 必须会推理。 */
    @NonNull
    public YoModelRequirement withReasoning() {
        return new YoModelRequirement(category, inputs, outputs, capabilities, true,
                minContextTokens, minOutputTokens);
    }

    /** 上下文窗口至少这么长。 */
    @NonNull
    public YoModelRequirement withMinContext(int tokens) {
        if (tokens < 1) {
            throw new IllegalArgumentException("minContext must be positive");
        }
        return new YoModelRequirement(category, inputs, outputs, capabilities, reasoning,
                tokens, minOutputTokens);
    }

    /** 单次输出上限至少这么长。 */
    @NonNull
    public YoModelRequirement withMinOutput(int tokens) {
        if (tokens < 1) {
            throw new IllegalArgumentException("minOutput must be positive");
        }
        return new YoModelRequirement(category, inputs, outputs, capabilities, reasoning,
                minContextTokens, tokens);
    }

    // ---- 取值 ----

    @NonNull
    public YoModelCategory category() {
        return category;
    }

    @NonNull
    public Set<YoModality> inputs() {
        return inputs;
    }

    @NonNull
    public Set<YoModality> outputs() {
        return outputs;
    }

    @NonNull
    public Set<YoModelCapability> capabilities() {
        return capabilities;
    }

    public boolean reasoning() {
        return reasoning;
    }

    @Nullable
    public Integer minContextTokens() {
        return minContextTokens;
    }

    @Nullable
    public Integer minOutputTokens() {
        return minOutputTokens;
    }

    // ---- 匹配 ----

    /**
     * 这个规格满不满足要求。逐条核对，任何一条明确不满足（或根本没人说过）就是 {@link YoModelFit#REJECTED}；
     * 全部满足时，只要有一条依据只是推断，结论就是 {@link YoModelFit#INFERRED}，否则是 {@link YoModelFit#CONFIRMED}。
     */
    @NonNull
    public YoModelFit fit(@NonNull YoModelSpec spec) {
        if (!spec.category().canServe(category)) {
            return YoModelFit.REJECTED;
        }
        boolean confirmed = spec.categorySource().trusted();
        for (YoModality modality : inputs) {
            YoFact fact = spec.input(modality);
            if (!fact.yes()) {
                return YoModelFit.REJECTED;
            }
            confirmed &= fact.confirmed();
        }
        for (YoModality modality : outputs) {
            YoFact fact = spec.output(modality);
            if (!fact.yes()) {
                return YoModelFit.REJECTED;
            }
            confirmed &= fact.confirmed();
        }
        for (YoModelCapability capability : capabilities) {
            YoFact fact = spec.capability(capability);
            if (!fact.yes()) {
                return YoModelFit.REJECTED;
            }
            confirmed &= fact.confirmed();
        }
        if (reasoning) {
            if (!spec.reasons()) {
                return YoModelFit.REJECTED;
            }
            confirmed &= spec.reasoning().confirmed();
        }
        Integer window = spec.limits().contextWindow();
        if (minContextTokens != null && window != null && window < minContextTokens) {
            return YoModelFit.REJECTED;
        }
        Integer output = spec.limits().maxOutputTokens();
        if (minOutputTokens != null && output != null && output < minOutputTokens) {
            return YoModelFit.REJECTED;
        }
        return confirmed ? YoModelFit.CONFIRMED : YoModelFit.INFERRED;
    }

    /**
     * 一句人话，错误提示和界面直接用：{@code 视觉模型，且需要：函数调用、推理、上下文不少于 128000 token}。
     */
    @NonNull
    public String describe() {
        List<String> extras = new ArrayList<>();
        for (YoModality modality : inputs) {
            extras.add(modality.label() + "输入");
        }
        for (YoModality modality : outputs) {
            extras.add(modality.label() + "输出");
        }
        for (YoModelCapability capability : capabilities) {
            extras.add(capability.label());
        }
        if (reasoning) {
            extras.add("推理");
        }
        if (minContextTokens != null) {
            extras.add("上下文不少于 " + minContextTokens + " token");
        }
        if (minOutputTokens != null) {
            extras.add("单次输出不少于 " + minOutputTokens + " token");
        }
        String base = category.label() + "模型";
        return extras.isEmpty() ? base : base + "，且需要：" + String.join("、", extras);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof YoModelRequirement that
                && category == that.category
                && inputs.equals(that.inputs)
                && outputs.equals(that.outputs)
                && capabilities.equals(that.capabilities)
                && reasoning == that.reasoning
                && Objects.equals(minContextTokens, that.minContextTokens)
                && Objects.equals(minOutputTokens, that.minOutputTokens);
    }

    @Override
    public int hashCode() {
        return Objects.hash(category, inputs, outputs, capabilities, reasoning,
                minContextTokens, minOutputTokens);
    }

    @NonNull
    @Override
    public String toString() {
        return "YoModelRequirement{" + describe() + " [" + category + "]}";
    }

    private static <E extends Enum<E>> EnumSet<E> copy(Set<E> source, Class<E> type) {
        EnumSet<E> copy = EnumSet.noneOf(type);
        copy.addAll(source);
        return copy;
    }
}
