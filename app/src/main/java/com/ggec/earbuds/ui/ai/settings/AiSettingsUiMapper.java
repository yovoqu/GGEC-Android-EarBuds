package com.ggec.earbuds.ui.ai.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.ai.model.AiActiveModel;
import com.ggec.earbuds.domain.ai.model.AiChatPreferences;
import com.ggec.earbuds.domain.ai.model.AiInteractionMode;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiSelection;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.model.AiVoiceConfig;
import com.ggec.earbuds.domain.ai.model.AiVoiceModel;

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

    @NonNull
    public AiSettingsUiState map(@NonNull AiSettings settings) {
        AiInteractionMode mode = settings.mode();
        return new AiSettingsUiState(true, mode, hint(mode), platforms(settings),
                identity(settings), voice(settings.voice()), style(settings));
    }

    @NonNull
    private static AiText hint(@NonNull AiInteractionMode mode) {
        return AiText.of(mode == AiInteractionMode.VOICE
                ? R.string.ai_settings_mode_voice_hint
                : R.string.ai_settings_mode_text_hint);
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
        String host = host(platform.baseUrl());
        if (host.isEmpty()) {
            return AiText.of(R.string.ai_settings_platform_models, count);
        }
        return AiText.of(R.string.ai_settings_platform_summary, count, host);
    }

    /** 副标题里不放协议头：一段 https:// 会把地址挤到看不见，而它从来不变。 */
    @NonNull
    private static String host(@Nullable String baseUrl) {
        if (baseUrl == null) {
            return "";
        }
        String value = baseUrl.strip();
        int scheme = value.indexOf("://");
        if (scheme >= 0) {
            value = value.substring(scheme + 3);
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
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
     * 语音卡只回答两件事：选的是哪家、这家有没有 Key。
     * 候选来自 {@link AiVoiceModel} 的预设顺序，界面不自己排序、也不自己补默认值。
     */
    @NonNull
    private static AiSettingsUiState.Voice voice(@NonNull AiVoiceConfig config) {
        List<AiSettingsUiState.VoiceOption> options = new ArrayList<>(AiVoiceModel.values().length);
        for (AiVoiceModel model : AiVoiceModel.values()) {
            options.add(new AiSettingsUiState.VoiceOption(model.id(), model.displayName(),
                    model == config.model(),
                    !config.apiKeys().getOrDefault(model, "").isBlank()));
        }
        return new AiSettingsUiState.Voice(config.model().id(), config.model().displayName(),
                config.configured(), options);
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
