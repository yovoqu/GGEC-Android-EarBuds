package com.ggec.earbuds.ui.ai.history;

import androidx.annotation.NonNull;

import java.util.List;

/** 历史面板四态投影：加载、空、内容、失败。失败时已有行仍可显示，由 View 决定是否同时展示失败提示。 */
record AiHistoryUiState(
        @NonNull List<AiHistoryItemUi> items,
        boolean loading,
        boolean content,
        boolean empty,
        boolean failed) {

    AiHistoryUiState {
        items = List.copyOf(items);
    }

    /** 首帧：还没有读过列表。 */
    @NonNull
    static AiHistoryUiState initial() {
        return new AiHistoryUiState(List.of(), true, false, false, false);
    }

    /**
     * 一次读取的终态：有行就是内容态，没行就是空态；读取失败单独标出，
     * 空列表 + 失败时显示失败态而不是“暂无会话”。
     */
    @NonNull
    static AiHistoryUiState of(@NonNull List<AiHistoryItemUi> items, boolean failed) {
        List<AiHistoryItemUi> copied = List.copyOf(items);
        boolean content = !copied.isEmpty();
        return new AiHistoryUiState(copied, false, content,
                copied.isEmpty() && !failed, failed);
    }
}
