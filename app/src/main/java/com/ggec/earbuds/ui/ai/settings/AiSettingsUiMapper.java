package com.ggec.earbuds.ui.ai.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.ai.model.AiActiveModel;
import com.ggec.earbuds.domain.ai.model.AiChatPreferences;
import com.ggec.earbuds.domain.ai.model.AiMode;
import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiSelection;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.model.AiUsageSummary;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 领域设置 → 设置总览页的界面状态。纯函数：不碰视图、不碰资源、不读时钟。
 *
 * <p>三处判断值得单独说：</p>
 * <ul>
 *   <li><b>平台行副标题</b>：填了 Key 才谈得上"有几个模型"，没填 Key 时那句「未填 Key」
 *       比「0 个模型」有用——后者会被读成"平台没有模型"。</li>
 *   <li><b>全双工置灰</b>：条件是"有没有实时语音模型"，不是"用户点没点"。没有实时模型时
 *       这一行不能点，副标题直接写缺什么。</li>
 *   <li><b>费用</b>：{@code pricedRequests == 0} 时显示「未上报」。{@code cost} 的 0 是未知，
 *       把它写成 $0.00 等于替平台宣布免费。</li>
 * </ul>
 */
public final class AiSettingsUiMapper {

    /** 提示词预览最多显示多少个字：一行副标题放得下的量，再多要进对话框看。 */
    static final int PROMPT_PREVIEW_LENGTH = 34;

    @NonNull
    public AiSettingsUiState map(@NonNull AiSettings settings, @NonNull AiUsageSummary usage) {
        return new AiSettingsUiState(true, settings.activeModel().map(active -> active.model().name()).orElse(null),
                platforms(settings), modes(settings), params(settings),
                usage(usage));
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

    // ---- 参数 ----

    private static AiSettingsUiState.Params params(AiSettings settings) {
        AiChatPreferences preferences = settings.preferences();
        int totalModels = 0;
        for (AiPlatformConfig platform : settings.platforms()) {
            totalModels += platform.models().size();
        }
        boolean hasValues = preferences.temperature() != null || preferences.topP() != null
                || preferences.maxOutputTokens() != null;
        return new AiSettingsUiState.Params(
                preferences.globalParamsEnabled(),
                preferences.systemPrompt(),
                promptPreview(preferences.systemPrompt()),
                preferences.temperature(),
                number(preferences.temperature(), 1),
                preferences.topP(),
                number(preferences.topP(), 2),
                preferences.maxOutputTokens(),
                preferences.maxOutputTokens() == null
                        ? AiText.of(R.string.ai_settings_value_platform_default)
                        : AiText.of(R.string.ai_settings_value_number,
                                String.valueOf(preferences.maxOutputTokens())),
                preferences.contextMessages(),
                context(preferences.contextMessages()),
                scope(settings),
                preferences.globalParamsEnabled() && hasValues && totalModels > 0,
                totalModels);
    }

    /**
     * 作用范围 = "哪些模型不会再跟随全局"，也就是覆盖表里的模型。
     *
     * <p>没有独立的 scope 字段，这里是<b>反推</b>：读覆盖表能说清的事实只有四种，
     * 说清了就够用；说不清的那些（例如"只想让半台设备生效"）本来也没有数据支撑，
     * 与其加一个没人维护的枚举，不如把事实写出来。</p>
     */
    @NonNull
    static AiSettingsUiState.Scope scope(@NonNull AiSettings settings) {
        List<AiPlatformConfig> platforms = settings.platforms();
        AiSelection selection = settings.selection();
        AiPlatformConfig activePlatform = null;
        String activeModelId = null;
        int pinnedTotal = 0;
        for (AiPlatformConfig platform : platforms) {
            pinnedTotal += pinned(platform);
            if (selection != null && platform.id().equals(selection.platformId())) {
                activePlatform = platform;
                activeModelId = selection.modelId();
            }
        }
        if (!settings.preferences().globalParamsEnabled()) {
            return new AiSettingsUiState.Scope(AiSettingsUiState.Scope.Kind.DISABLED, null, pinnedTotal);
        }
        if (pinnedTotal == 0) {
            return new AiSettingsUiState.Scope(AiSettingsUiState.Scope.Kind.ALL, null, 0);
        }
        // 只有一个模型有自己的值，而且正好是当前模型：这是"仅这个模型"那一种。
        if (pinnedTotal == 1 && activePlatform != null && activeModelId != null
                && activePlatform.modelParams().containsKey(activeModelId)) {
            String name = activePlatform.model(activeModelId)
                    .map(AiModel::name).orElse(activeModelId);
            return new AiSettingsUiState.Scope(AiSettingsUiState.Scope.Kind.ONE_MODEL, name, 1);
        }
        // 当前平台的每个模型都有自己的值、别处一个都没有：这是"当前平台"。
        if (activePlatform != null && !activePlatform.models().isEmpty()
                && pinned(activePlatform) == activePlatform.models().size()
                && pinned(activePlatform) == pinnedTotal) {
            return new AiSettingsUiState.Scope(AiSettingsUiState.Scope.Kind.PLATFORM,
                    activePlatform.name(), pinnedTotal);
        }
        // 其余情况是"某几个模型各自固定"。副行要能回答"哪几个"，所以把前两个名字带上。
        return new AiSettingsUiState.Scope(AiSettingsUiState.Scope.Kind.PARTIAL,
                pinnedNames(platforms), pinnedTotal);
    }

    /**
     * 前两个"有自己参数"的模型名，用顿号连起来。
     *
     * <p>副行只有一行宽，全列出来会挤成一团；点名两个 + 总数，足够让人认出是哪一批。</p>
     */
    @Nullable
    private static String pinnedNames(@NonNull List<AiPlatformConfig> platforms) {
        StringBuilder names = new StringBuilder();
        int shown = 0;
        for (AiPlatformConfig platform : platforms) {
            for (AiModel model : platform.models()) {
                if (!platform.modelParams().containsKey(model.id())) {
                    continue;
                }
                if (shown == 2) {
                    return names.toString();
                }
                if (shown > 0) {
                    names.append('、');
                }
                names.append(model.name());
                shown++;
            }
        }
        return shown == 0 ? null : names.toString();
    }

    /** 平台上有多少个"仍然存在的模型"被单独设过值。已下线模型的残留覆盖不算。 */
    private static int pinned(@NonNull AiPlatformConfig platform) {
        int count = 0;
        for (AiModel model : platform.models()) {
            if (platform.modelParams().containsKey(model.id())) {
                count++;
            }
        }
        return count;
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

    /** 采样参数为空表示沿用平台默认，界面必须说"平台默认"，不能编一个数字出来。 */
    @NonNull
    private static AiText number(@Nullable Double value, int decimals) {
        if (value == null) {
            return AiText.of(R.string.ai_settings_value_platform_default);
        }
        return AiText.of(R.string.ai_settings_value_number,
                String.format(Locale.ROOT, "%." + decimals + "f", value));
    }

    @NonNull
    private static AiText context(int messages) {
        return messages <= 0
                ? AiText.of(R.string.ai_settings_value_context_all)
                : AiText.of(R.string.ai_settings_value_context, String.valueOf(messages));
    }

    // ---- 用量 ----

    private static AiSettingsUiState.Usage usage(AiUsageSummary summary) {
        if (summary.requests() <= 0) {
            return new AiSettingsUiState.Usage(false, null, null, null, false);
        }
        AiText requests = AiText.of(R.string.ai_settings_usage_requests_value,
                String.valueOf(summary.requests()));
        AiText tokens = AiText.of(R.string.ai_settings_usage_tokens_value,
                count(summary.inputTokens()), count(summary.outputTokens()));
        boolean priced = summary.hasPricedRequests();
        AiText cost = priced
                ? AiText.of(R.string.ai_settings_usage_cost_value, amount(summary.cost()),
                        String.valueOf(summary.pricedRequests()))
                : AiText.of(R.string.ai_settings_usage_cost_unknown);
        return new AiSettingsUiState.Usage(true, requests, tokens, cost, priced);
    }

    /** 金额按美元两位小数；四舍五入只影响显示，累计仍用 {@link BigDecimal} 本身。 */
    @NonNull
    static String amount(@NonNull BigDecimal cost) {
        return cost.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** 令牌数是平台报什么就显示什么，不做 K/M 缩写：静默改单位会让人对不上账单。 */
    @NonNull
    static String count(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }
}
