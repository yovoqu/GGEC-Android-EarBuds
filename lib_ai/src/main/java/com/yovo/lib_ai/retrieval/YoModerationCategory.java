package com.yovo.lib_ai.retrieval;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 一个审核类别在本次判定里的状态与分数，以及它针对哪些输入模态生效。
 *
 * <p>{@link #appliedInputTypes()} 对应 OpenAI 的 {@code category_applied_input_types}，
 * 因此"文本输入的分"和"图片输入的分"可以分开读，符合多模态分开计分的口径。</p>
 */
public final class YoModerationCategory {

    private final String name;
    private final boolean flagged;
    private final double score;
    private final List<String> appliedInputTypes;

    public YoModerationCategory(
            @NonNull String name,
            boolean flagged,
            double score,
            @NonNull List<String> appliedInputTypes) {
        this.name = name;
        this.flagged = flagged;
        this.score = score;
        this.appliedInputTypes = Collections.unmodifiableList(appliedInputTypes);
    }

    @NonNull
    public String name() {
        return name;
    }

    public boolean flagged() {
        return flagged;
    }

    public double score() {
        return score;
    }

    @NonNull
    public List<String> appliedInputTypes() {
        return appliedInputTypes;
    }

    @NonNull
    @Override
    public String toString() {
        return "YoModerationCategory{name=" + name + ", flagged=" + flagged + ", score=" + score + '}';
    }

    /** 便于整体转成"类别名 → 分数"。 */
    @NonNull
    public static Map<String, Double> scores(@NonNull List<YoModerationCategory> categories) {
        Map<String, Double> map = new java.util.LinkedHashMap<>();
        for (YoModerationCategory category : categories) {
            map.put(category.name(), category.score());
        }
        return map;
    }
}
