package com.ggec.earbuds.ui.ai.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.ggec.earbuds.domain.ai.model.AiInteractionMode;

import java.util.List;

/**
 * 设置总览页的界面状态：顶部模式分段、身份卡、当前模式的分区，全部是"已经做完取舍"的结果。
 *
 * <p>这里没有 {@code AiSettings}：界面不该知道 {@code AiPlatformConfig} 里那些只跟请求有关的字段，
 * 也不该自己判断"这个模型算不算实时模型"。判断都在 {@link AiSettingsUiMapper} 里，可以单测。</p>
 *
 * @param mode     当前分段选中的接入形态
 * @param modeHint 分段下方那行说明，告诉用户这一模式接的是哪类模型
 */
public record AiSettingsUiState(
        boolean loaded,
        @NonNull AiInteractionMode mode,
        @NonNull AiText modeHint,
        List<PlatformRow> platforms,
        @NonNull Identity identity,
        @NonNull Style style) {

    public AiSettingsUiState {
        platforms = List.copyOf(platforms);
    }

    /**
     * 平台分区的一行。
     *
     * @param current 当前模式选中的模型就在这个平台上；尾部会显示「当前」
     */
    public record PlatformRow(@NonNull String platformId, @NonNull String name, @NonNull AiText subtitle,
                              boolean current) {
    }

    /**
     * 身份卡要回答"我在用什么"：当前模式选中的平台与模型。
     *
     * @param platformName 为空表示这一模式还没配好；空态文案按模式给
     */
    public record Identity(@Nullable String platformName, @Nullable String modelName,
                           @StringRes int emptyTitle, @StringRes int emptyHint) {

        public boolean empty() {
            return platformName == null;
        }
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
        return new AiSettingsUiState(false, AiInteractionMode.defaultMode(), AiText.of(0),
                List.of(), new Identity(null, null, 0, 0), new Style("", AiText.of(0)));
    }
}
