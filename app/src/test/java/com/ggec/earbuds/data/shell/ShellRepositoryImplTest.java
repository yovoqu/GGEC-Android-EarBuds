package com.ggec.earbuds.data.shell;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.ggec.earbuds.domain.shell.ShellDestination;
import com.ggec.earbuds.domain.shell.ShellTab;

import org.junit.Test;

import java.util.List;

public class ShellRepositoryImplTest {

    @Test
    public void loadsDeviceAiMine() {
        List<ShellTab> tabs = new ShellRepositoryImpl(new ShellCatalogSource(), new ShellTabMapper()).loadTabs();

        assertEquals(List.of(ShellDestination.DEVICE, ShellDestination.AI, ShellDestination.MINE),
                tabs.stream().map(ShellTab::destination).toList());
    }

    @Test
    public void mapperRejectsUnknownRecord() {
        try {
            new ShellTabMapper().toDomain(new ShellTabRecord("sound"));
            fail("unknown record must be rejected");
        } catch (IllegalArgumentException expected) {
            assertEquals("Unknown destination: sound", expected.getMessage());
        }
    }
}
