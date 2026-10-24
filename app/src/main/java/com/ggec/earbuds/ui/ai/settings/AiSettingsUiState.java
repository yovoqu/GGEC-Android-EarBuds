package com.ggec.earbuds.ui.ai.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.model.AiMode;

import java.util.List;

/**
 * 设置总览页的界面状态：四个分区要画的东西，全部是"已经做完取舍"的结果。
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
        Params params,
        Usage usage) {

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
     * 对话参数：{@code *Text} 是右侧尾部显示的值，原始值留给编辑对话框做初值。
     *
     * @param scope      "这套值对谁生效"。数据上没有独立的 scope 字段，它是从
     *                   {@code AiPlatformConfig.modelParams} 反推出来的事实（见 {@link Scope}）
     * @param canApply   能不能用"应用到全部模型"：全局开着、至少有一项值、且本机确实有模型
     * @param totalModels 本机所有平台的模型总数，批量应用前要告诉用户会写多少个
     */
    public record Params(boolean globalParamsEnabled,
                         @NonNull String systemPrompt, @NonNull AiText promptPreview,
                         @Nullable Double temperature, @NonNull AiText temperatureText,
                         @Nullable Double topP, @NonNull AiText topPText,
                         @Nullable Integer maxOutputTokens, @NonNull AiText maxOutputText,
                         int contextMessages, @NonNull AiText contextText,
                         @NonNull Scope scope, boolean canApply, int totalModels) {
    }

    /**
     * 作用范围：全局参数最终落在谁身上。
     *
     * <p>这一版没有"作用范围"这个可写字段，也不打算加一个半成品字段：
     * 它由"哪些模型有参数覆盖"反推——没有覆盖就是所有模型都用全局，
     * 覆盖了哪些就是哪些不再跟随。反推出的四种事实分别对应
     * 所有模型 / 当前平台 / 仅某个模型 / 部分模型。</p>
     *
     * @param subject 副行里要指名道姓的东西：仅某个模型时是模型名、当前平台时是平台名、
     *                部分模型时是"前两个模型名用顿号连起来"；没得说就为 null
     * @param pinned 有自己的参数、不再跟随全局的模型数
     */
    public record Scope(@NonNull Kind kind, @Nullable String subject, int pinned) {

        public enum Kind {
            /** 全局开关关着：这套值不会发给任何模型。 */
            DISABLED,
            /** 没有任何模型单独设置：全部用这套值。 */
            ALL,
            /** 只有当前模型有覆盖。 */
            ONE_MODEL,
            /** 当前平台的每个模型都有覆盖，别的平台没有。 */
            PLATFORM,
            /** 其余情况：N 个模型各自固定。 */
            PARTIAL
        }
    }

    /**
     * 用量：只有 {@code hasRecords} 为真时才有数字可显示。
     *
     * <p>没有记录时页面写「还没有记录」，而不是 0 次 / 0.00——后者会让人以为真的算过账。
     * {@code costKnown} 为假表示有请求但平台没报过钱，这时显示「未上报」，0 元是未知不是免费。</p>
     */
    public record Usage(boolean hasRecords, @Nullable AiText requests, @Nullable AiText tokens,
                        @Nullable AiText cost, boolean costKnown) {
    }

    @NonNull
    public static AiSettingsUiState empty() {
        return new AiSettingsUiState(false, null, List.of(), List.of(),
                new Params(false, "", AiText.of(0), null, AiText.of(0), null, AiText.of(0), null, AiText.of(0), 0,
                        AiText.of(0), new Scope(Scope.Kind.DISABLED, null, 0), false, 0),
                new Usage(false, null, null, null, false));
    }
}
