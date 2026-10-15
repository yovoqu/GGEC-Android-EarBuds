package com.ggec.earbuds.ui.shell;

import static org.junit.Assert.assertEquals;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.shell.ShellDestination;
import com.ggec.earbuds.domain.shell.ShellTab;

import org.junit.Test;

import java.util.List;

public class ShellUiMapperTest {

    @Test
    public void mapsDomainTabsToPresentation() {
        List<ShellTab> tabs = List.of(
                new ShellTab(ShellDestination.DEVICE),
                new ShellTab(ShellDestination.AI),
                new ShellTab(ShellDestination.MINE));
        ShellUiState state = new ShellUiMapper().map(tabs, tabs.get(1));

        assertEquals("ai", state.selectedKey());
        assertEquals(2, state.selectedTabId());
        assertEquals(R.string.tab_ai, state.selected().titleRes());
        assertEquals(R.drawable.youi_hds_ic_discover, state.tabs().get(1).iconRes());
        assertEquals("device", state.keyForTab(1));
    }
}
