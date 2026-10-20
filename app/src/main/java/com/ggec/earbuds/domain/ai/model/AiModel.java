package com.ggec.earbuds.domain.ai.model;

import androidx.annotation.Nullable;

import java.util.Objects;
import java.util.Set;

/**
 * 平台上一个可用于对话的模型。{@code manual} 为真表示用户手动添加，重新拉取列表时保留。
 */
public record AiModel(
        String id,
        String name,
        @Nullable Integer contextWindow,
        @Nullable Integer maxOutputTokens,
        Set<AiModelFeature> features,
        boolean manual) {

    public AiModel {
        Objects.requireNonNull(id, "id");
        name = name == null || name.isBlank() ? id : name;
        features = Set.copyOf(features);
    }

    public static AiModel manual(String id) {
        return new AiModel(id, id, null, null, Set.of(), true);
    }

    public boolean reasoning() {
        return features.contains(AiModelFeature.REASONING);
    }
}
