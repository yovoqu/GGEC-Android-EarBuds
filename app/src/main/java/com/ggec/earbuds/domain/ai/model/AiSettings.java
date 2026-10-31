package com.ggec.earbuds.domain.ai.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * AI 功能的全部用户设置：已添加的平台、当前正在配置哪种接入形态、两种形态各自的模型。
 *
 * <p><b>为什么两种形态的选择分开存，而且类型都不一样</b>：文本模式用 DeepSeek 这类对话模型，
 * 它是"某个平台上的某个模型"，所以记成 {@link AiSelection}；语音模式用实时模型，
 * 它是 APP 预设的一项能力（{@link AiVoiceModel}），有自己的端点和 Key，所以记成
 * {@link AiVoiceConfig}。共用一个选中项的结果是切到语音发现"模型不支持"，
 * 再回设置里改一次；共用一种类型的结果是语音又被绑回文本平台。</p>
 *
 * @param mode          当前正在配置的接入形态；只影响设置页展示，不影响另一模式的选择
 * @param textSelection 文本模式选中的平台与对话模型
 * @param voice         语音模式选中的预设模型与它自己的 Key
 */
public record AiSettings(
        List<AiPlatformConfig> platforms,
        AiInteractionMode mode,
        @Nullable AiSelection textSelection,
        @NonNull AiVoiceConfig voice,
        AiChatPreferences preferences) {

    public static final AiSettings EMPTY = new AiSettings(List.of(), AiInteractionMode.defaultMode(),
            null, AiVoiceConfig.DEFAULT, AiChatPreferences.DEFAULT);

    public AiSettings {
        platforms = List.copyOf(platforms);
        mode = mode == null ? AiInteractionMode.defaultMode() : mode;
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
                return new AiSettings(next, mode, textSelection, voice, preferences);
            }
        }
        next.add(config);
        return new AiSettings(next, mode, textSelection, voice, preferences);
    }

    /**
     * 删掉平台时，只有指向它的文本选择失效；语音选择与平台无关，原样保留。
     */
    public AiSettings withoutPlatform(String id) {
        List<AiPlatformConfig> next = new ArrayList<>(platforms);
        next.removeIf(platform -> platform.id().equals(id));
        AiSelection keptText = textSelection != null && textSelection.platformId().equals(id)
                ? null : textSelection;
        return new AiSettings(next, mode, keptText, voice, preferences);
    }

    public AiSettings withTextSelection(@Nullable AiSelection selection) {
        return new AiSettings(platforms, mode, selection, voice, preferences);
    }

    public AiSettings withVoice(@NonNull AiVoiceConfig next) {
        return new AiSettings(platforms, mode, textSelection, next, preferences);
    }

    public AiSettings withMode(@NonNull AiInteractionMode next) {
        return new AiSettings(platforms, next, textSelection, voice, preferences);
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
        return new AiSettings(platforms, mode, null, voice, preferences);
    }

    private boolean valid(@Nullable AiSelection selection) {
        if (selection == null) {
            return false;
        }
        Optional<AiPlatformConfig> platform = platform(selection.platformId());
        return platform.isPresent() && platform.get().model(selection.modelId()).isPresent();
    }

    public AiSettings withPreferences(AiChatPreferences preferences) {
        return new AiSettings(platforms, mode, textSelection, voice, preferences);
    }
}
