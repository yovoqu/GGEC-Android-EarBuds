package com.ggec.earbuds.domain.ai.model;

import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * 平台上一个可选的模型。{@code manual} 为真表示用户手动添加，重新拉取列表时保留。
 *
 * <p>实时模型很少出现在平台的 {@code /models} 列表里；用户手写一个实时 id 时也要认出来，
 * 否则它会被当成对话模型列进文本列表，选中后请求必然 404。</p>
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
        return new AiModel(id, id, null, null,
                looksRealtime(id) ? Set.of(AiModelFeature.REALTIME_AUDIO) : Set.of(), true);
    }

    public boolean reasoning() {
        return features.contains(AiModelFeature.REASONING);
    }

    /** 能不能直接接一条实时语音连接。 */
    public boolean realtime() {
        return features.contains(AiModelFeature.REALTIME_AUDIO);
    }

    /**
     * 按 id 判断实时模型。只用于"用户手写 id"这一种场景：
     * 平台拉回来的模型带的是平台自己报的能力位，不在这里猜。
     */
    public static boolean looksRealtime(@Nullable String id) {
        if (id == null) {
            return false;
        }
        String lower = id.toLowerCase(Locale.ROOT);
        return lower.contains("realtime") || lower.contains("omni") || lower.contains("audio-preview");
    }
}
