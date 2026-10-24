package com.ggec.earbuds.domain.ai.model;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * AI 功能的全部用户设置：已添加的平台、当前选中的模型、对话参数。
 * 选中的模型只是一个引用，是否仍然可用要由 {@link #activeModel()} 现算。
 */
public record AiSettings(
        List<AiPlatformConfig> platforms,
        @Nullable AiSelection selection,
        AiChatPreferences preferences,
        AiVoiceSettings voice) {

    public static final AiSettings EMPTY = new AiSettings(List.of(), null, AiChatPreferences.DEFAULT,
            AiVoiceSettings.defaults());
    public AiSettings {
        platforms = List.copyOf(platforms);
        Objects.requireNonNull(preferences, "preferences");
        voice = voice == null ? AiVoiceSettings.defaults() : voice;
    }

    public Optional<AiPlatformConfig> platform(String id) {
        for (AiPlatformConfig platform : platforms) {
            if (platform.id().equals(id)) {
                return Optional.of(platform);
            }
        }
        return Optional.empty();
    }

    public boolean hasConfiguredPlatform() {
        for (AiPlatformConfig platform : platforms) {
            if (platform.configured()) {
                return true;
            }
        }
        return false;
    }

    public Optional<AiActiveModel> activeModel() {
        if (selection == null) {
            return Optional.empty();
        }
        Optional<AiPlatformConfig> platform = platform(selection.platformId());
        if (platform.isEmpty() || !platform.get().configured()) {
            return Optional.empty();
        }
        return platform.get().model(selection.modelId())
                .map(model -> new AiActiveModel(platform.get(), model));
    }

    /** 当前模型（不看平台有没有 API Key）：对话页据此区分"先选择模型"和"当前平台没有 API Key"。 */
    public Optional<AiActiveModel> selectedModel() {
        if (selection == null) {
            return Optional.empty();
        }
        Optional<AiPlatformConfig> platform = platform(selection.platformId());
        if (platform.isEmpty()) {
            return Optional.empty();
        }
        return platform.get().model(selection.modelId())
                .map(model -> new AiActiveModel(platform.get(), model));
    }

    public boolean isCurrent(String platformId, String modelId) {
        return selection != null && selection.platformId().equals(platformId)
                && selection.modelId().equals(modelId);
    }

    public AiSettings withPlatform(AiPlatformConfig config) {
        List<AiPlatformConfig> next = new ArrayList<>(platforms);
        for (int i = 0; i < next.size(); i++) {
            if (next.get(i).id().equals(config.id())) {
                next.set(i, config);
                return new AiSettings(next, selection, preferences, voice);
            }
        }
        next.add(config);
        return new AiSettings(next, selection, preferences, voice);
    }

    /**
     * 删掉平台时，指向它的选中项一并失效。
     */
    public AiSettings withoutPlatform(String id) {
        List<AiPlatformConfig> next = new ArrayList<>(platforms);
        next.removeIf(platform -> platform.id().equals(id));
        AiSelection kept = selection != null && selection.platformId().equals(id) ? null : selection;
        return new AiSettings(next, kept, preferences, voice);
    }

    public AiSettings withSelection(@Nullable AiSelection selection) {
        return new AiSettings(platforms, selection, preferences, voice);
    }

    /**
     * 选中的模型已经不在平台的列表里（重新拉取后下线、被删除、换了主机）时，清掉这个悬空引用。
     */
    public AiSettings withValidSelection() {
        if (selection == null) {
            return this;
        }
        Optional<AiPlatformConfig> platform = platform(selection.platformId());
        if (platform.isPresent() && platform.get().model(selection.modelId()).isPresent()) {
            return this;
        }
        return new AiSettings(platforms, null, preferences, voice);
    }

    public AiSettings withVoice(AiVoiceSettings voice) {
        return new AiSettings(platforms, selection, preferences, voice);
    }

    public AiSettings withPreferences(AiChatPreferences preferences) {
        return new AiSettings(platforms, selection, preferences, voice);
    }
}
