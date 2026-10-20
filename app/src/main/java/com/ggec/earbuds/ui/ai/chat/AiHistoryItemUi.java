package com.ggec.earbuds.ui.ai.chat;

/**
 * 历史列表的一行。{@code title} 为空表示对话还没有命名，页面显示默认名。
 */
public record AiHistoryItemUi(
        String id,
        String title,
        String preview,
        long updatedAtMillis,
        int messageCount,
        boolean current) {
}
