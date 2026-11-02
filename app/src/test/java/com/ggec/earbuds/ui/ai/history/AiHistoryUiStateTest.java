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
        AiHistoryUiState state = AiHistoryUiState.from(false, false, List.of());

        assertTrue(state.loading());
        assertFalse(state.content());
        assertFalse(state.empty());
        assertFalse(state.failed());
    }

    @Test
    public void emptyAndFailedAreDistinctAfterRead() {
        AiHistoryUiState empty = AiHistoryUiState.from(true, false, List.of());
        AiHistoryUiState failed = AiHistoryUiState.from(true, true, List.of());

        assertTrue(empty.empty());
        assertFalse(empty.failed());
        assertTrue(failed.failed());
        assertFalse(failed.empty());
    }

    @Test
    public void rowsProduceContentOnlyWhenLoaded() {
        List<AiHistoryItemUi> rows = List.of(new AiHistoryItemUi(
                "id", AiHistoryText.raw("title"), null, AiHistoryText.raw("now"),
                false, AiHistoryItemUi.Group.TODAY));

        assertTrue(AiHistoryUiState.from(true, false, rows).content());
        assertFalse(AiHistoryUiState.from(false, false, rows).content());
    }
}
