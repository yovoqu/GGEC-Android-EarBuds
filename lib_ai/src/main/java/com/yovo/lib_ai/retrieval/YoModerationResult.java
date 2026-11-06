package com.yovo.lib_ai.retrieval;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 内容审核结果。
 *
 * <p>{@link #flagged()} 是整体判定；{@link #categories()} 给出每个类别的状态与分数，
 * 多模态输入按 {@link YoModerationCategory#appliedInputTypes()} 区分文本/图片。</p>
 */
public final class YoModerationResult {

    private final boolean flagged;
    private final List<YoModerationCategory> categories;
    @Nullable
    private final String model;
    private final String rawJson;

    public YoModerationResult(
            boolean flagged,
            @NonNull List<YoModerationCategory> categories,
            @Nullable String model,
            @NonNull String rawJson) {
        this.flagged = flagged;
        this.categories = Collections.unmodifiableList(new ArrayList<>(categories));
        this.model = model;
        this.rawJson = rawJson;
    }

    public boolean flagged() {
        return flagged;
    }

    @NonNull
    public List<YoModerationCategory> categories() {
        return categories;
    }

    /** 按类别名取分数；不存在返回 {@code null}。 */
    @Nullable
    public Double score(@NonNull String category) {
        for (YoModerationCategory item : categories) {
            if (item.name().equals(category)) {
                return item.score();
            }
        }
        return null;
    }

    @Nullable
    public String model() {
        return model;
    }

    @NonNull
    public String rawJson() {
        return rawJson;
    }
}
