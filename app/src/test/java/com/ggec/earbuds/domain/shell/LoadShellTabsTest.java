package com.ggec.earbuds.domain.shell;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.util.List;

public class LoadShellTabsTest {

    @Test
    public void returnsRepositorySnapshot() {
        List<ShellTab> tabs = List.of(new ShellTab(ShellDestination.AI));

        assertEquals(tabs, new LoadShellTabs(() -> tabs).execute());
    }

    @Test
    public void rejectsEmptyCatalog() {
        try {
            new LoadShellTabs(() -> List.of()).execute();
            fail("empty catalog must be rejected");
        } catch (IllegalStateException expected) {
            assertEquals("Shell catalog is empty", expected.getMessage());
        }
    }
}
