package com.ggec.earbuds.domain.shell;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.util.List;

public class ShellSelectionPolicyTest {

    private final ShellSelectionPolicy policy = new ShellSelectionPolicy();

    @Test
    public void firstFollowsCatalogOrder() {
        List<ShellTab> tabs = List.of(
                new ShellTab(ShellDestination.MINE),
                new ShellTab(ShellDestination.AI));

        assertEquals(ShellDestination.MINE, policy.first(tabs).destination());
    }

    @Test
    public void selectFindsTabByKey() {
        List<ShellTab> tabs = List.of(
                new ShellTab(ShellDestination.DEVICE),
                new ShellTab(ShellDestination.AI));

        assertEquals(ShellDestination.AI, policy.select(tabs, "ai").destination());
    }

    @Test
    public void selectRejectsUnknownKey() {
        List<ShellTab> tabs = List.of(new ShellTab(ShellDestination.DEVICE));

        try {
            policy.select(tabs, "sound");
            fail("unknown key must be rejected");
        } catch (IllegalArgumentException expected) {
            assertEquals("Unknown tab: sound", expected.getMessage());
        }
    }
}
