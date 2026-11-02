package com.ggec.earbuds.ui.ai.history;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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

    @NonNull
    static AiHistoryUiState from(@Nullable Boolean loadedValue,
                                 @Nullable Boolean failedValue,
                                 @Nullable List<AiHistoryItemUi> itemValue) {
        boolean loaded = Boolean.TRUE.equals(loadedValue);
        boolean loadFailed = Boolean.TRUE.equals(failedValue);
        List<AiHistoryItemUi> items = itemValue == null ? List.of() : itemValue;
        boolean content = loaded && !items.isEmpty();
        return new AiHistoryUiState(items, !loaded, content,
                loaded && items.isEmpty() && !loadFailed,
                loadFailed);
    }
}
