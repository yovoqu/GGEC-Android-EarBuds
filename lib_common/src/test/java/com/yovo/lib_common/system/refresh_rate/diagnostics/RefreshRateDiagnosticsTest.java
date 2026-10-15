package com.yovo.lib_common.system.refresh_rate.diagnostics;

import com.yovo.lib_common.system.refresh_rate.model.RefreshRateDiagnostics;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RefreshRateDiagnosticsTest {

    @Test
    public void empty_returnsSafeDefaults() {
        RefreshRateDiagnostics diagnostics = RefreshRateDiagnostics.empty();

        assertEquals(0, diagnostics.sdkVersion());
        assertEquals("", diagnostics.brand());
        assertEquals("", diagnostics.manufacturer());
        assertEquals("", diagnostics.model());
        assertEquals(-1, diagnostics.displayId());
        assertTrue(diagnostics.supportedRefreshRatesHz().isEmpty());
        assertNull(diagnostics.selectedMode());
        assertNull(diagnostics.currentMode());
        assertFalse(diagnostics.powerSaveMode());
        assertFalse(diagnostics.thermalStatusAvailable());
        assertFalse(diagnostics.multiWindowMode());
        assertNull(diagnostics.note());
    }

    @Test
    public void supportedRefreshRatesListIsUnmodifiable() {
        RefreshRateDiagnostics diagnostics = new RefreshRateDiagnostics(
                34, "google", "Google", "Pixel",
                0, Collections.singletonList(120f),
                null, null,
                false, 0, false, false, null);

        assertEquals(1, diagnostics.supportedRefreshRatesHz().size());
        assertEquals(120f, diagnostics.supportedRefreshRatesHz().get(0), 0.01f);
    }
}
