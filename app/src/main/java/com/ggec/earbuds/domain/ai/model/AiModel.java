package com.ggec.earbuds.domain.ai.model;

import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * 平台上一个可选的模型。{@code manual} 为真表示用户手动添加，重新拉取列表时保留。
 *
 * <p>实时模型很少出现在平台的 {@code /models} 列表里，用户只能手写 id；
 * 所以 {@link #manual(String)} 会按 id 判断它是不是实时模型——否则手写进来的
 * {@code gpt-4o-realtime-preview} 会被文本模式当成对话模型，语音模式又找不到它。</p>
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
