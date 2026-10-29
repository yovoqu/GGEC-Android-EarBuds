package com.ggec.earbuds.ui.ai.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.model.AiMode;

import java.util.List;

/**
 * 设置总览页的界面状态：三个分区要画的东西，全部是"已经做完取舍"的结果。
 *
 * <p>这里没有 {@code AiSettings}：界面不该知道 {@code AiPlatformConfig} 里那些只跟请求有关的字段，
 * 也不该自己判断"要不要显示 N 个模型"。判断都在 {@link AiSettingsUiMapper} 里，可以单测。</p>
 */
public record AiSettingsUiState(
        boolean loaded,
        /** 当前选中的模型 id；没有可用平台或没选模型时是 null。身份卡要回答"我在用谁"。 */
        @Nullable String activeModel,
        List<PlatformRow> platforms,
        List<ModeRow> modes,
        Style style) {

    public AiSettingsUiState {
        platforms = List.copyOf(platforms);
        modes = List.copyOf(modes);
    }

    /**
     * 平台分区的一行。
     *
     * @param current 当前模型就选在这个平台上；尾部会显示「当前」
     */
    public record PlatformRow(@NonNull String platformId, @NonNull String name, @NonNull AiText subtitle,
                              boolean current) {
    }

    /**
     * AI 模式的一行。
     *
     * @param enabled 这一行能不能点：没有可用的实时语音模型时全双工是灰的，
     *                副标题写清原因，而不是让用户点进去看一句失败
     */
    public record ModeRow(@NonNull AiMode mode, int title, @NonNull AiText subtitle,
                          boolean selected, boolean enabled) {
    }

    /**
     * 回答风格：现在只剩系统提示词。
     *
     * <p>温度、top_p、最大输出、上下文条数曾经也在这里，现在由 APP 在发请求时统一给定，
     * 所以界面状态里不再有它们的影子——界面没有可画的东西，就不该留着字段。</p>
     *
     * @param promptPreview 一行副标题放得下的预览，完整提示词在 {@code systemPrompt} 里
     */
    public record Style(@NonNull String systemPrompt, @NonNull AiText promptPreview) {
    }

    @NonNull
    public static AiSettingsUiState empty() {
        return new AiSettingsUiState(false, null, List.of(), List.of(),
                new Style("", AiText.of(0)));
    }
}
