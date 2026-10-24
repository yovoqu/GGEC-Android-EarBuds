package com.ggec.earbuds.domain.ai.model;

import androidx.annotation.Nullable;

import java.util.Objects;
import java.util.Set;

/**
 * 平台上的一个模型。
 *
 * <p>{@code manual}：用户手动添加，重新拉取时保留、不会被标记为已下线。
 * {@code kind}：模型类型，非对话模型不测速、不能设为当前。
 * {@code remoteMissing}：最近一次拉取时远端不再返回（"已下线"），不自动删除。</p>
 */
public record AiModel(
        String id,
        String name,
        @Nullable Integer contextWindow,
        @Nullable Integer maxOutputTokens,
        Set<AiModelFeature> features,
        boolean manual,
        AiModelKind kind,
        boolean remoteMissing) {

    public AiModel {
        Objects.requireNonNull(id, "id");
        name = name == null || name.isBlank() ? id : name;
        features = Set.copyOf(features);
        kind = kind == null ? AiModelKind.CHAT : kind;
    }

    /** 兼容旧调用：对话模型、未下线。 */
    public AiModel(String id, String name, @Nullable Integer contextWindow, @Nullable Integer maxOutputTokens,
                   Set<AiModelFeature> features, boolean manual) {
        this(id, name, contextWindow, maxOutputTokens, features, manual, AiModelKind.CHAT, false);
    }

    public static AiModel manual(String id) {
        return new AiModel(id, id, null, null, Set.of(), true);
    }

    public boolean reasoning() {
        return features.contains(AiModelFeature.REASONING);
    }

    public boolean chat() {
        return kind.chat();
    }

    /** 有不同于 ID 的显示名。 */
    public boolean hasDisplayName() {
        return !name.equals(id);
    }

    public AiModel withRemoteMissing(boolean missing) {
        return new AiModel(id, name, contextWindow, maxOutputTokens, features, manual, kind, missing);
    }

    public AiModel withDetails(String newName, @Nullable Integer newContextWindow, AiModelKind newKind) {
        return new AiModel(id, newName, newContextWindow, maxOutputTokens, features, manual, newKind,
                remoteMissing);
    }
}
