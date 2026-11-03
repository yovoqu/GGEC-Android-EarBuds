package com.ggec.earbuds.domain.ai.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * AI 功能的全部用户配置：已添加的平台、文本模式选中项、语音模式预设与对话偏好。
 *
 * <p><b>当前分段不在这里</b>：它只决定设置页展示哪一块，是视图偏好，存在
 * {@code AiUiPreferences}；写进配置文档会让每次切 tab 都落一次盘。</p>
 *
 * @param textSelection 文本模式选中的平台与对话模型
 * @param voice         语音模式选中的预设模型与它自己的 Key
 */
public record AiSettings(
        List<AiPlatformConfig> platforms,
        @Nullable AiSelection textSelection,
        @NonNull AiVoiceConfig voice,
        AiChatPreferences preferences) {

    public static final AiSettings EMPTY = new AiSettings(List.of(),
            null, AiVoiceConfig.DEFAULT, AiChatPreferences.DEFAULT);

    public AiSettings {
        platforms = List.copyOf(platforms);
        voice = voice == null ? AiVoiceConfig.DEFAULT : voice;
        Objects.requireNonNull(preferences, "preferences");
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

    /** 文本模式当前可用的平台与模型。 */
    public Optional<AiActiveModel> textModel() {
        if (textSelection == null) {
            return Optional.empty();
        }
        Optional<AiPlatformConfig> platform = platform(textSelection.platformId());
        if (platform.isEmpty() || !platform.get().configured()) {
            return Optional.empty();
        }
        return platform.get().model(textSelection.modelId())
                .map(model -> new AiActiveModel(platform.get(), model));
    }

    /**
     * 语音模式能不能拨号：预设模型已经选好，并且它自己的 Key 已填。
     * 它不查平台——语音模型与文本平台没有任何关系。
     */
    public boolean voiceReady() {
        return voice.configured();
    }

    public AiSettings withPlatform(AiPlatformConfig config) {
        List<AiPlatformConfig> next = new ArrayList<>(platforms);
        for (int i = 0; i < next.size(); i++) {
            if (next.get(i).id().equals(config.id())) {
                next.set(i, config);
                return new AiSettings(next, textSelection, voice, preferences);
            }
        }
        next.add(config);
        return new AiSettings(next, textSelection, voice, preferences);
    }

    /**
     * 删掉平台时，只有指向它的文本选择失效；语音选择与平台无关，原样保留。
     */
    public AiSettings withoutPlatform(String id) {
        List<AiPlatformConfig> next = new ArrayList<>(platforms);
        next.removeIf(platform -> platform.id().equals(id));
        AiSelection keptText = textSelection != null && textSelection.platformId().equals(id)
                ? null : textSelection;
        return new AiSettings(next, keptText, voice, preferences);
    }

    public AiSettings withTextSelection(@Nullable AiSelection selection) {
        return new AiSettings(platforms, selection, voice, preferences);
    }

    public AiSettings withVoice(@NonNull AiVoiceConfig next) {
        return new AiSettings(platforms, textSelection, next, preferences);
    }

    /**
     * 选中的文本模型已经不在平台的列表里（重新拉取后下线、被删除、换了主机）时，清掉这个悬空引用。
     * 语音选择不参与：它是预设表里的枚举，不会因为平台变动而失效。
     */
    public AiSettings withValidSelections() {
        boolean validText = valid(textSelection);
        if (validText || textSelection == null) {
            return this;
        }
        return new AiSettings(platforms, null, voice, preferences);
    }

    private boolean valid(@Nullable AiSelection selection) {
        if (selection == null) {
            return false;
        }
        Optional<AiPlatformConfig> platform = platform(selection.platformId());
        return platform.isPresent() && platform.get().model(selection.modelId()).isPresent();
    }

    public AiSettings withPreferences(AiChatPreferences preferences) {
        return new AiSettings(platforms, textSelection, voice, preferences);
    }
}
