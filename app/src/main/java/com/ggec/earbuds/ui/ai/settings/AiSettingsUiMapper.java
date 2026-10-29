package com.ggec.earbuds.ui.ai.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.ai.model.AiActiveModel;
import com.ggec.earbuds.domain.ai.model.AiChatPreferences;
import com.ggec.earbuds.domain.ai.model.AiMode;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 领域设置 → 设置总览页的界面状态。纯函数：不碰视图、不碰资源、不读时钟。
 *
 * <p>两处判断值得单独说：</p>
 * <ul>
 *   <li><b>平台行副标题</b>：填了 Key 才谈得上"有几个模型"，没填 Key 时那句「未填 Key」
 *       比「0 个模型」有用——后者会被读成"平台没有模型"。</li>
 *   <li><b>全双工置灰</b>：条件是"有没有实时语音模型"，不是"用户点没点"。没有实时模型时
 *       这一行不能点，副标题直接写缺什么。</li>
 * </ul>
 */
public final class AiSettingsUiMapper {

    /** 提示词预览最多显示多少个字：一行副标题放得下的量，再多要进对话框看。 */
    static final int PROMPT_PREVIEW_LENGTH = 34;

    @NonNull
    public AiSettingsUiState map(@NonNull AiSettings settings) {
        return new AiSettingsUiState(true, settings.activeModel().map(active -> active.model().name()).orElse(null),
                platforms(settings), modes(settings), style(settings));
    }

    // ---- 平台 ----

    private static List<AiSettingsUiState.PlatformRow> platforms(AiSettings settings) {
        Optional<AiActiveModel> active = settings.activeModel();
        String activePlatformId = active.map(model -> model.platform().id()).orElse(null);
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

    // ---- 模式 ----

    private static List<AiSettingsUiState.ModeRow> modes(AiSettings settings) {
        AiMode mode = settings.voice().mode();
        // 真实条件是"当前平台有没有具备 REALTIME_AUDIO 能力的模型"；这一轮先看用户有没有指定实时模型，
        // 判据只有一处，等能力模型接进设置层时只改这里。
        String realtimeModel = settings.voice().realtimeModel();
        boolean realtimeReady = realtimeModel != null && !realtimeModel.isBlank();
        AiText omniSubtitle = realtimeReady
                ? AiText.of(R.string.ai_settings_mode_omni_hint)
                : AiText.of(R.string.ai_settings_mode_omni_needs_model);
        return List.of(
                new AiSettingsUiState.ModeRow(AiMode.CASCADED, R.string.ai_call_mode_cascaded,
                        AiText.of(R.string.ai_settings_mode_cascaded_hint), mode == AiMode.CASCADED, true),
                new AiSettingsUiState.ModeRow(AiMode.OMNI, R.string.ai_call_mode_omni,
                        omniSubtitle, mode == AiMode.OMNI, realtimeReady));
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
