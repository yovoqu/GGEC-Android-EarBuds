package com.ggec.earbuds.domain.shell;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

public class ShellDestinationTest {

    @Test
    public void fromKeyResolvesAi() {
        assertEquals(ShellDestination.AI, ShellDestination.fromKey("ai"));
    }

    @Test
    public void fromKeyRejectsUnknownKey() {
        try {
            ShellDestination.fromKey("sound");
            fail("unknown key must be rejected");
        } catch (IllegalArgumentException expected) {
            assertEquals("Unknown destination: sound", expected.getMessage());
        }
    }
}
