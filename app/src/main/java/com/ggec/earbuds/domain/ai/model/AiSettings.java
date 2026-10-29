package com.ggec.earbuds.domain.ai.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * AI 功能的全部用户设置：已添加的平台、当前正在配置哪种接入形态、两种形态各自选中的模型。
 *
 * <p><b>为什么选择要按模式分开存</b>：文本模式用 DeepSeek 这类对话模型，语音模式用实时
 * 全/多模态模型，两者根本不是同一个模型。共用一个选中项的结果是——切到语音发现"模型不支持"，
 * 再回设置里改一次；切回文本又得改回来。分开存之后，两边各记各的，切换只是换视图。</p>
 *
 * <p>选中的模型只是一个引用，是否仍然可用要由 {@link #textModel()} / {@link #voiceModel()} 现算。</p>
 *
 * @param mode          当前正在配置的接入形态；只影响设置页展示，不影响另一模式的选择
 * @param textSelection 文本模式选中的平台与对话模型
 * @param voiceSelection 语音模式选中的平台与实时模型
 */
public record AiSettings(
        List<AiPlatformConfig> platforms,
        AiInteractionMode mode,
        @Nullable AiSelection textSelection,
        @Nullable AiSelection voiceSelection,
        AiChatPreferences preferences) {

    public static final AiSettings EMPTY = new AiSettings(List.of(), AiInteractionMode.defaultMode(),
            null, null, AiChatPreferences.DEFAULT);

    public AiSettings {
        platforms = List.copyOf(platforms);
        mode = mode == null ? AiInteractionMode.defaultMode() : mode;
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
        return resolve(textSelection);
    }

    /** 语音模式当前可用的平台与实时模型。 */
    public Optional<AiActiveModel> voiceModel() {
        return resolve(voiceSelection);
    }

    @Nullable
    public AiSelection selection(@NonNull AiInteractionMode mode) {
        return mode == AiInteractionMode.VOICE ? voiceSelection : textSelection;
    }

    private Optional<AiActiveModel> resolve(@Nullable AiSelection selection) {
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

    public AiSettings withPlatform(AiPlatformConfig config) {
        List<AiPlatformConfig> next = new ArrayList<>(platforms);
        for (int i = 0; i < next.size(); i++) {
            if (next.get(i).id().equals(config.id())) {
                next.set(i, config);
                return new AiSettings(next, mode, textSelection, voiceSelection, preferences);
            }
        }
        next.add(config);
        return new AiSettings(next, mode, textSelection, voiceSelection, preferences);
    }

    /**
     * 删掉平台时，指向它的两个选择一并失效——文本和语音都不该留下悬空引用。
     */
    public AiSettings withoutPlatform(String id) {
        List<AiPlatformConfig> next = new ArrayList<>(platforms);
        next.removeIf(platform -> platform.id().equals(id));
        AiSelection keptText = textSelection != null && textSelection.platformId().equals(id)
                ? null : textSelection;
        AiSelection keptVoice = voiceSelection != null && voiceSelection.platformId().equals(id)
                ? null : voiceSelection;
        return new AiSettings(next, mode, keptText, keptVoice, preferences);
    }

    public AiSettings withSelection(@NonNull AiInteractionMode target, @Nullable AiSelection selection) {
        return target == AiInteractionMode.VOICE
                ? new AiSettings(platforms, mode, textSelection, selection, preferences)
                : new AiSettings(platforms, mode, selection, voiceSelection, preferences);
    }

    public AiSettings withMode(@NonNull AiInteractionMode next) {
        return new AiSettings(platforms, next, textSelection, voiceSelection, preferences);
    }

    /**
     * 选中的模型已经不在平台的列表里（重新拉取后下线、被删除、换了主机）时，清掉这个悬空引用。
     * 两种模式各判各的：语音模型下线不该顺手把文本模型也清掉。
     */
    public AiSettings withValidSelections() {
        AiSelection validText = valid(textSelection) ? textSelection : null;
        AiSelection validVoice = valid(voiceSelection) ? voiceSelection : null;
        if (validText == textSelection && validVoice == voiceSelection) {
            return this;
        }
        return new AiSettings(platforms, mode, validText, validVoice, preferences);
    }

    private boolean valid(@Nullable AiSelection selection) {
        if (selection == null) {
            return false;
        }
        Optional<AiPlatformConfig> platform = platform(selection.platformId());
        return platform.isPresent() && platform.get().model(selection.modelId()).isPresent();
    }

    public AiSettings withPreferences(AiChatPreferences preferences) {
        return new AiSettings(platforms, mode, textSelection, voiceSelection, preferences);
    }
}
