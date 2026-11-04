package com.ggec.earbuds.domain.ai.model;

import androidx.annotation.Nullable;

import java.util.Objects;
import java.util.Set;

/**
 * 平台上一个可选的模型。{@code manual} 为真表示用户手动添加，重新拉取列表时保留。
 *
 * <p>{@code category} 是 SDK 规格上的类别。手写 id 没有规格，类别为空，不从名字推断。</p>
 */
public record AiModel(
        String id,
        String name,
        @Nullable Integer contextWindow,
        @Nullable Integer maxOutputTokens,
        Set<AiModelFeature> features,
        boolean manual,
        @Nullable AiModelCategory category) {

    public AiModel {
        Objects.requireNonNull(id, "id");
        name = name == null || name.isBlank() ? id : name;
        features = Set.copyOf(features);
    }

    public static AiModel manual(String id) {
        return new AiModel(id, id, null, null, Set.of(), true, null);
    }

    public boolean reasoning() {
        return features.contains(AiModelFeature.REASONING);
    }
}
