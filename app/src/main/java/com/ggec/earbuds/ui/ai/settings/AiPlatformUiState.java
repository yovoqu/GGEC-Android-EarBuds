package com.ggec.earbuds.ui.ai.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * 平台详情页的状态（MVVM 的 M→V 唯一出口）。
 *
 * <p>纯数据：页面拿到它只做"摆位置"。每条业务规则都在 {@link AiPlatformUiMapper} 里算好，
 * 这样规则可以单测，页面只能错在布局上。</p>
 *
 * <p>区块顺序：连接 → 模型 → 高级 → 危险区。身份由页面标题承担，不单独成块。</p>
 */
public record AiPlatformUiState(
        boolean loaded,
        @NonNull String title,
        @Nullable String badge,
        @NonNull Connection connection,
        @NonNull Models models,
        @NonNull Advanced advanced,
        boolean advancedExpanded,
        boolean canDelete) {

    /** 连接区：只回答"通不通"。 */
    public record Connection(@NonNull Key key, @Nullable String result, boolean testing,
                             @Nullable String blockedReason) {
    }

    public record Key(boolean filled, @NonNull String display) {
    }

    /**
     * 模型区：标题 + 动作 + 行。
     *
     * @param count      平台上的模型总数（不随搜索变化，标题写的是总数）
     * @param rows       当前可见的行：搜索框里有关键字时只有匹配的那些
     * @param searchable 模型超过 8 个才给搜索框
     * @param query      当前搜索词（已 trim）；空串表示没在搜
     */
    public record Models(int count, @NonNull List<Row> rows, @NonNull String actionLabel,
                         boolean speedRunning, @Nullable String summary,
                         boolean searchable, @NonNull String query) {
        public Models {
            rows = List.copyOf(rows);
        }
    }

    /**
     * 一个模型行。
     *
     * @param facts 事实片段，页面用「 · 」连接
     * @param state 这一行处于哪一步
     */
    public record Row(@NonNull String id, @NonNull String name, boolean selected,
                      @NonNull List<String> facts, @NonNull RowState state,
                      /** 首字毫秒，未测或失败为 -1。区块头比较用它——不能拿展示文本去比。 */
                      long firstTokenMillis) {
        public Row {
            facts = List.copyOf(facts);
        }
    }

    public enum RowState {
        IDLE,
        TESTING,
        OK,
        FAILED
    }

    /** 高级区：收起时只显示一行摘要。 */
    public record Advanced(@NonNull String name, boolean nameEditable, @NonNull String baseUrl,
                           @androidx.annotation.StringRes int protocolRes,
                           boolean protocolChoosable,
                           /** 当前模型的生成参数与来源，如「温度 0.7（此模型）· top_p 平台默认」。 */
                           @NonNull String params) {
    }

    @NonNull
    public static AiPlatformUiState empty() {
        return new AiPlatformUiState(false, "", null,
                new Connection(new Key(false, ""), null, false, null),
                new Models(0, List.of(), "", false, null, false, ""),
                new Advanced("", false, "", 0, false, ""),
                false, false);
    }
}
