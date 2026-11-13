package com.ggec.earbuds.ui.ai.settings;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.ai.model.AiActiveModel;
import com.ggec.earbuds.domain.ai.model.AiChatPreferences;
import com.ggec.earbuds.domain.ai.model.AiInteractionMode;
import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiSelection;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.model.AiSettingsPersistenceStatus;
import com.ggec.earbuds.domain.ai.model.AiVoiceConfig;
import com.ggec.earbuds.domain.ai.model.AiVoiceModel;
import com.ggec.earbuds.domain.ai.voice.VoiceDuplex;
import com.ggec.earbuds.ui.ai.common.AiFormat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 领域设置 → 设置总览页的界面状态。纯函数：不碰视图、不碰资源、不读时钟。
 *
 * <p>核心取舍只有一条：<b>文本与语音各用各的数据源</b>。文本模式看
 * {@link AiSettings#textModel()}（平台上的对话模型），语音模式看
 * {@link AiSettings#voice()}（代码里预设的语音模型）。语音不经过平台，
 * 所以这里没有任何"把实时模型从平台列表里挑出来"的逻辑。</p>
 */
public final class AiSettingsUiMapper {

    /** 提示词预览最多显示多少个字：一行副标题放得下的量，再多要进对话框看。 */
    static final int PROMPT_PREVIEW_LENGTH = 34;

    /**
     * @param mode 展示用的接入形态。它由界面偏好提供（{@link com.ggec.earbuds.domain.ai.port.AiUiPreferences}），
     *             不进设置文档——切分段只是换一个视图，不该触发配置落盘。
     */
    @NonNull
    public AiSettingsUiState map(@NonNull AiSettings settings, @NonNull AiInteractionMode mode) {
        return map(settings, mode, true);
    }

    /**
     * @param loaded 是否已经拿到首个快照。页面在首帧之前也会用空设置投影一次（模板/持久化状态先到），
     *               那时仍是加载态，不能标成已加载。
     */
    @NonNull
    public AiSettingsUiState map(@NonNull AiSettings settings, @NonNull AiInteractionMode mode,
                                 boolean loaded) {
        // 持久化状态与平台模板是页面级数据，由 ViewModel 用 withPersistence/withTemplates 叠加；
        // mapper 只把领域设置翻成界面状态，保持纯函数。
        return new AiSettingsUiState(loaded, false, mode, hint(mode), platforms(settings),
                identity(settings), voice(settings.voice()), style(settings),
                AiSettingsPersistenceStatus.SAVED, List.of());
    }

    @NonNull
    private static AiText hint(@NonNull AiInteractionMode mode) {
        return AiSettingsUiState.modeHint(mode);
    }

    // ---- 平台 ----

    private static List<AiSettingsUiState.PlatformRow> platforms(AiSettings settings) {
        AiSelection selection = settings.textSelection();
        String activePlatformId = selection == null ? null : selection.platformId();
        List<AiSettingsUiState.PlatformRow> rows = new ArrayList<>(settings.platforms().size());
        for (AiPlatformConfig platform : settings.platforms()) {
            rows.add(new AiSettingsUiState.PlatformRow(platform.id(), platform.name(),
                    platformSubtitle(platform), platform.id().equals(activePlatformId)));
        }
        return rows;
    }

    /**
     * 有 Key：说清"几个模型 + 连的是哪台主机"；换过自定义地址的平台最容易在这里看出问题。
     */
    @NonNull
    static AiText platformSubtitle(@NonNull AiPlatformConfig platform) {
        if (!platform.configured()) {
            return AiText.of(R.string.ai_settings_platform_no_key);
        }
        String count = String.valueOf(platform.models().size());
        String host = AiFormat.host(platform.baseUrl());
        if (host.isEmpty()) {
            return AiText.of(R.string.ai_settings_platform_models, count);
        }
        return AiText.of(R.string.ai_settings_platform_summary, count, host);
    }

    // ---- 文本身份卡 ----

    @NonNull
    private static AiSettingsUiState.Identity identity(AiSettings settings) {
        Optional<AiActiveModel> active = settings.textModel();
        if (active.isEmpty()) {
            return new AiSettingsUiState.Identity(null, null,
                    R.string.ai_settings_text_empty, R.string.ai_settings_text_empty_hint);
        }
        return new AiSettingsUiState.Identity(active.get().platform().name(),
                active.get().model().name(), 0, 0);
    }

    // ---- 语音模型 ----

    /**
     * 服务商下拉来自 {@link AiVoiceModel} 的预设顺序。地址、模型原样交给界面，
     * 映射层不决定选哪一个；拉到的模型都是实时类别，类别保持 SDK 的原值；
     * 默认双工模式同样在此映射完毕，视图层不直接接触领域枚举。
     */
    @NonNull
    private static AiSettingsUiState.Voice voice(@NonNull AiVoiceConfig config) {
        List<AiSettingsUiState.VoiceOption> options = new ArrayList<>(AiVoiceModel.values().length);
        for (AiVoiceModel model : AiVoiceModel.values()) {
            options.add(new AiSettingsUiState.VoiceOption(model.id(), model.displayName(),
                    model == config.model(),
                    config.configuredProviders().contains(model),
                    icon(model)));
        }
        List<AiSettingsUiState.VoiceModelOption> models =
                new ArrayList<>(config.realtimeModels().size());
        for (AiModel listed : config.realtimeModels()) {
            models.add(new AiSettingsUiState.VoiceModelOption(listed.id()));
        }
        VoiceDuplex defaultDuplex = config.defaultDuplex();
        return new AiSettingsUiState.Voice(config.model().id(), config.model().displayName(),
                config.configured(), options,
                config.effectiveBaseUrl(), !config.baseUrl().isEmpty(),
                config.realtimeModelId(), models, false,
                defaultDuplex.id(), duplexOptions(defaultDuplex), icon(config.model()));
    }

    /** 服务商图标：归属映射层，视图不再按厂商 id 自己 switch。 */
    @DrawableRes
    private static int icon(@NonNull AiVoiceModel model) {
        return switch (model) {
            case CHATGPT -> R.drawable.ic_ai_provider_chatgpt;
            case QWEN -> R.drawable.ic_ai_provider_qwen;
        };
    }

    @NonNull
    private static List<AiSettingsUiState.DuplexOption> duplexOptions(@NonNull VoiceDuplex selected) {
        VoiceDuplex[] ordered = {VoiceDuplex.FULL_DUPLEX, VoiceDuplex.HALF_DUPLEX};
        List<AiSettingsUiState.DuplexOption> items = new ArrayList<>(ordered.length);
        for (VoiceDuplex mode : ordered) {
            items.add(new AiSettingsUiState.DuplexOption(
                    mode.id(), duplexLabel(mode), mode == selected));
        }
        return items;
    }

    @StringRes
    private static int duplexLabel(@NonNull VoiceDuplex mode) {
        return switch (mode) {
            case FULL_DUPLEX -> R.string.ai_settings_voice_duplex_full;
            case HALF_DUPLEX -> R.string.ai_settings_voice_duplex_half;
        };
    }

    // ---- 回答风格 ----

    /**
     * 只剩系统提示词。生成参数已经收回 APP：界面不画滑杆，也就不需要把它们映射成文本。
     */
    private static AiSettingsUiState.Style style(AiSettings settings) {
        String prompt = settings.preferences().systemPrompt();
        return new AiSettingsUiState.Style(prompt, promptPreview(prompt));
    }

    /** 一行副标题只放得下一行字：换行压成空格，超出的截断，全文留给对话框。 */
    @NonNull
    static AiText promptPreview(@NonNull String systemPrompt) {
        String flat = systemPrompt.replace('\n', ' ').replace('\r', ' ').strip();
        while (flat.contains("  ")) {
            flat = flat.replace("  ", " ");
        }
        if (flat.isEmpty()) {
            return AiText.of(R.string.ai_settings_prompt_empty);
        }
        if (flat.equals(AiChatPreferences.DEFAULT_SYSTEM_PROMPT.strip())) {
            return AiText.of(R.string.ai_settings_prompt_default);
        }
        if (flat.length() <= PROMPT_PREVIEW_LENGTH) {
            return AiText.raw(flat);
        }
        return AiText.raw(flat.substring(0, PROMPT_PREVIEW_LENGTH) + "…");
    }
}
