package com.yovo.lib_ai.models.spec;

import androidx.annotation.NonNull;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * 模型的推理能力：会不会想、能不能关、能不能调力度、能不能限定思考预算。
 *
 * <p>整个推理面共用一个出处，因为这几项几乎总是一起被说出来的（平台的 {@code thinking} 能力块，
 * 或用户在同一个面板里一起填）。思维链长度这种数字不在这里，它是 {@link YoModelLimits#maxReasoningTokens()}。</p>
 *
 * @param mode             怎么想
 * @param efforts          可选的力度档位，空表示不可调或没人说过
 * @param budgetAdjustable 能否限定思考 token 预算（Claude 的 {@code budget_tokens} 这一类）
 * @param source           以上说法是谁给的；{@link YoReasoningMode#UNKNOWN} 时没有意义
 */
public record YoReasoning(
        @NonNull YoReasoningMode mode,
        @NonNull Set<YoReasoningEffort> efforts,
        boolean budgetAdjustable,
        @NonNull YoSpecSource source) {

    /** 没人说过。 */
    public static final YoReasoning UNKNOWN =
            new YoReasoning(YoReasoningMode.UNKNOWN, Set.of(), false, YoSpecSource.INFERRED);

    public YoReasoning {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(source, "source");
        efforts = immutable(efforts);
    }

    /**
     * 只有"怎么想"，没有档位和预算。
     */
    @NonNull
    public static YoReasoning of(@NonNull YoReasoningMode mode, @NonNull YoSpecSource source) {
        return new YoReasoning(mode, Set.of(), false, source);
    }

    /** 会产出思考内容。 */
    public boolean reasons() {
        return mode.reasons();
    }

    /** 会推理，并且出处可信。 */
    public boolean confirmed() {
        return reasons() && source.trusted();
    }

    /** 同一个模型可以关掉思考。 */
    public boolean canDisable() {
        return mode == YoReasoningMode.SWITCHABLE;
    }

    /** 力度档位可调。 */
    public boolean effortAdjustable() {
        return !efforts.isEmpty();
    }

    @NonNull
    public YoReasoning withEfforts(@NonNull Collection<YoReasoningEffort> levels) {
        return new YoReasoning(mode, immutable(levels), budgetAdjustable, source);
    }

    @NonNull
    public YoReasoning withBudgetAdjustable(boolean adjustable) {
        return new YoReasoning(mode, efforts, adjustable, source);
    }

    @NonNull
    private static Set<YoReasoningEffort> immutable(Collection<YoReasoningEffort> source) {
        if (source == null || source.isEmpty()) {
            return Set.of();
        }
        EnumSet<YoReasoningEffort> copy = EnumSet.noneOf(YoReasoningEffort.class);
        copy.addAll(source);
        return Collections.unmodifiableSet(copy);
    }
}
