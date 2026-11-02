package com.ggec.earbuds.ui.ai.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.domain.ai.port.AiCancellable;

import org.junit.Test;

import java.util.List;

public class PlatformSpeedTestStateTest {

    @Test
    public void aRunOwnsOneGenerationAndFinishingInvalidatesLateCallbacks() {
        PlatformSpeedTestState state = new PlatformSpeedTestState();
        long before = state.generation();

        state.begin(List.of("a", "b"));

        long run = state.generation();
        assertTrue(run > before);
        assertTrue(state.running());
        assertEquals(2, state.testing().size());

        state.finish();

        assertFalse(state.running());
        assertTrue(state.testing().isEmpty());
        assertTrue("finishing must invalidate callbacks from the run", run < state.generation());
    }

    @Test
    public void cancellingDetachesTheCurrentRunAndInvalidatesIt() {
        PlatformSpeedTestState state = new PlatformSpeedTestState();
        state.begin(List.of("a"));
        AiCancellable current = () -> {
        };
        state.attach(current);
        long run = state.generation();

        AiCancellable detached = state.detach();
        state.invalidate();

        assertEquals(current, detached);
        assertNull(state.detach());
        assertTrue(run < state.generation());
    }
}
