package com.ggec.earbuds.ui.ai.history;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/** 历史面板四态互斥。 */
public class AiHistoryUiStateTest {

    @Test
    public void loadingIsExclusiveBeforeFirstRead() {
        AiHistoryUiState state = AiHistoryUiState.initial();

        assertTrue(state.loading());
        assertFalse(state.content());
        assertFalse(state.empty());
        assertFalse(state.failed());
    }

    @Test
    public void emptyAndFailedAreDistinctAfterRead() {
        AiHistoryUiState empty = AiHistoryUiState.of(List.of(), false);
        AiHistoryUiState failed = AiHistoryUiState.of(List.of(), true);

        assertTrue(empty.empty());
        assertFalse(empty.failed());
        assertTrue(failed.failed());
        assertFalse(failed.empty());
    }

    @Test
    public void rowsProduceContentAndEmptyRowsDoNot() {
        List<AiHistoryItemUi> rows = List.of(new AiHistoryItemUi(
                "id", AiHistoryText.raw("title"), null, AiHistoryText.raw("now"),
                false, AiHistoryItemUi.Group.TODAY));

        assertTrue(AiHistoryUiState.of(rows, false).content());
        assertFalse(AiHistoryUiState.of(List.of(), false).content());
        assertTrue("失败但仍有行时：内容态与失败标记同时成立，由 View 决定是否叠加提示",
                AiHistoryUiState.of(rows, true).failed());
        assertTrue(AiHistoryUiState.of(rows, true).content());
    }
}
