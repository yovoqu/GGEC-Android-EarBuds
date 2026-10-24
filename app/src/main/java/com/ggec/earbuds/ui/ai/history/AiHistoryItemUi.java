package com.ggec.earbuds.ui.ai.history;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 历史面板里的一行，已经做完取舍：文案、时刻、是不是当前这条、落在哪个分组。
 *
 * <p>面板只按这个记录画，不再回头看 {@code AiConversationSummary}：一行里"标题为空显示什么"
 * "多久算今天""截到几个字"这类判断都在 {@link AiHistoryUiMapper} 里，可以单测。</p>
 *
 * @param preview 没有摘要时为 {@code null}：整行只显示标题，行高从 72dp 回到 56dp
 * @param current 就是对话页现在打开的那条。面板给它 brand 色的勾并加粗标题，
 *                而不是只靠一行背景色——背景色在深浅色下都不够明确
 * @param group   今天 / 昨天 / 更早。分组标题由面板按这个值插入，日历计算留在 Mapper 里
 */
public record AiHistoryItemUi(
        @NonNull String id,
        @NonNull AiHistoryText title,
        @Nullable AiHistoryText preview,
        @NonNull AiHistoryText time,
        boolean current,
        @NonNull Group group) {

    /** 列表的分组。顺序即显示顺序：最近的在上。 */
    public enum Group {
        TODAY,
        YESTERDAY,
        EARLIER
    }
}
