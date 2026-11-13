package com.ggec.earbuds.ui.ai.settings;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.ai.model.AiInteractionMode;
import com.ggec.earbuds.domain.ai.model.AiPlatformTemplate;
import com.ggec.earbuds.domain.ai.model.AiSettingsPersistenceStatus;

import java.util.List;
import java.util.Objects;

/**
 * 设置总览页的界面状态：顶部模式分段、文本身份卡、语音模型卡，全部是"已经做完取舍"的结果。
 *
 * <p>这里没有 {@code AiSettings}：界面不该知道 {@code AiPlatformConfig} 里那些只跟请求有关的字段，
 * 也不该自己改判模型类别。类别来自 SDK，{@link AiSettingsUiMapper} 只把它交给界面。</p>
 *
 * @param mode       当前分段选中的接入形态
 * @param modeHint   分段下方那行说明，告诉用户这一模式接的是哪类模型
 * @param identity   文本模式的身份卡；语音模式不画它
 * @param voice      语音模式的设置状态：服务商、地址、实时模型、Key 状态与通话交流模式
 * @param loadFailed 首次读取失败；此时设置内容尚不可用
 */
public record AiSettingsUiState(
        boolean loaded,
        boolean loadFailed,
        @NonNull AiInteractionMode mode,
        @NonNull AiText modeHint,
        List<PlatformRow> platforms,
        @NonNull Identity identity,
        @NonNull Voice voice,
        @NonNull Style style,
        @NonNull AiSettingsPersistenceStatus persistence,
        @NonNull List<AiPlatformTemplate> templates) {

    public AiSettingsUiState {
        platforms = List.copyOf(platforms);
        Objects.requireNonNull(persistence, "persistence");
        templates = List.copyOf(templates);
    }

    /**
     * 平台分区的一行。
     *
     * @param current 文本模式选中的模型就在这个平台上；尾部会显示「当前」
     */
    public record PlatformRow(@NonNull String platformId, @NonNull String name, @NonNull AiText subtitle,
                              boolean current) {
    }

    /**
     * 文本模式的身份卡要回答"我在用什么"：选中的平台与模型。
     *
     * @param platformName 为空表示还没有可用的文本模型
     */
    public record Identity(@Nullable String platformName, @Nullable String modelName,
                           @StringRes int emptyTitle, @StringRes int emptyHint) {

        public boolean empty() {
            return platformName == null;
        }
    }

    /**
     * 语音模式的设置状态：服务商、地址、拉到的实时模型、Key 状态与通话交流模式。
     *
     * @param configured      当前服务商的 Key 是否已填；没填时通话入口不可用
     * @param options         服务商下拉/列表，顺序即预设顺序
     * @param baseUrl         真正会去连的主机，没改过时是官方地址
     * @param baseUrlCustom   true 表示用户改过地址，界面据此说"自定义/官方"
     * @param realtimeModelId 真正会去连的模型；没拉列表时是厂商预设
     * @param models          已拉取的实时模型，空表示还没拉过
     * @param fetching        正在拉模型列表
     * @param defaultDuplexId 当前默认双工模式标识
     * @param duplexOptions   通话交流模式分段的展示项（有序）
     */
    public record Voice(@NonNull String modelId, @NonNull String modelName, boolean configured,
                        @NonNull List<VoiceOption> options,
                        @NonNull String baseUrl, boolean baseUrlCustom,
                        @NonNull String realtimeModelId, @NonNull List<VoiceModelOption> models,
                        boolean fetching, @NonNull String defaultDuplexId,
                        @NonNull List<DuplexOption> duplexOptions,
                        @DrawableRes int providerIconRes) {

        public Voice {
            options = List.copyOf(options);
            models = List.copyOf(models);
            defaultDuplexId = Objects.requireNonNull(defaultDuplexId, "defaultDuplexId");
            duplexOptions = List.copyOf(duplexOptions);
        }

        @NonNull
        public Voice fetching(boolean value) {
            return new Voice(modelId, modelName, configured, options, baseUrl, baseUrlCustom,
                    realtimeModelId, models, value, defaultDuplexId, duplexOptions,
                    providerIconRes);
        }
    }

    /** 已拉取模型列表里的一项。语音页只列实时模型，所以只需要 id。 */
    public record VoiceModelOption(@NonNull String id) {
    }

    /** 通话交流模式分段项：纯展示数据，View 不接触领域枚举。 */
    public record DuplexOption(@NonNull String id, @StringRes int labelRes, boolean current) {
    }

    /**
     * 下拉里的一项。
     *
     * @param configured 这一家是否已经存过 Key：切换前就能看出"切过去还差什么"
     */
    public record VoiceOption(@NonNull String id, @NonNull String name, boolean current,
                              boolean configured, @DrawableRes int iconRes) {
    }

    /**
     * 回答风格：只有系统提示词，且只在文本模式出现。
     *
     * <p>温度、top_p、最大输出、上下文条数由 APP 在发请求时统一给定，
     * 所以界面状态里不再有它们的影子——界面没有可画的东西，就不该留着字段。</p>
     *
     * @param promptPreview 一行副标题放得下的预览，完整提示词在 {@code systemPrompt} 里
     */
    public record Style(@NonNull String systemPrompt, @NonNull AiText promptPreview) {
    }

    @NonNull
    public static AiSettingsUiState empty() {
        return new AiSettingsUiState(false, false, AiInteractionMode.defaultMode(), AiText.of(0),
                List.of(), new Identity(null, null, 0, 0),
                new Voice("", "", false, List.of(), "", false, "", List.of(), false,
                        "", List.of(), 0),
                new Style("", AiText.of(0)),
                AiSettingsPersistenceStatus.SAVED, List.of());
    }

    @NonNull
    public AiSettingsUiState failedLoading() {
        return new AiSettingsUiState(false, true, mode, modeHint, platforms, identity, voice, style,
                persistence, templates);
    }

    /**
     * 只更换接入形态：不重算平台/身份/语音等任何分区。
     *
     * <p>切分段本质上只影响两个字段（mode 与说明行），所以给状态机一个"切片更新"入口，
     * 避免为了换一个 tab 又把整页状态重建一遍。</p>
     */
    @NonNull
    public AiSettingsUiState withMode(@NonNull AiInteractionMode next) {
        if (next == mode) {
            return this;
        }
        return new AiSettingsUiState(loaded, loadFailed, next, modeHint(next), platforms, identity,
                voice, style, persistence, templates);
    }

    /** 分段下方那行说明：单源，mapper 与状态机都用它，不各写一份资源选择。 */
    @NonNull
    public static AiText modeHint(@NonNull AiInteractionMode mode) {
        return AiText.of(mode == AiInteractionMode.VOICE
                ? R.string.ai_settings_mode_voice_hint
                : R.string.ai_settings_mode_text_hint);
    }

    @NonNull
    public AiSettingsUiState withFetchingVoice(boolean fetching) {
        return new AiSettingsUiState(loaded, loadFailed, mode, modeHint, platforms, identity,
                voice.fetching(fetching), style, persistence, templates);
    }

    @NonNull
    public AiSettingsUiState withPersistence(@NonNull AiSettingsPersistenceStatus next) {
        if (next == persistence) {
            return this;
        }
        return new AiSettingsUiState(loaded, loadFailed, mode, modeHint, platforms, identity, voice,
                style, next, templates);
    }

    @NonNull
    public AiSettingsUiState withTemplates(@NonNull List<AiPlatformTemplate> next) {
        List<AiPlatformTemplate> copied = List.copyOf(next);
        if (copied.equals(templates)) {
            return this;
        }
        return new AiSettingsUiState(loaded, loadFailed, mode, modeHint, platforms, identity, voice,
                style, persistence, copied);
    }
}
