package com.yovo.lib_common.system.refresh_rate.display;

import com.yovo.lib_common.system.refresh_rate.internal.display.RefreshRateModeSelector;
import com.yovo.lib_common.system.refresh_rate.internal.display.RefreshRateModeSnapshot;

import com.yovo.lib_common.system.refresh_rate.model.RefreshRateMode;
import com.yovo.lib_common.system.refresh_rate.model.RefreshRatePolicy;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RefreshRateModeSelectorTest {

    private static RefreshRateModeSnapshot snapshot(int currentWidth,
                                                    int currentHeight,
                                                    RefreshRateMode... modes) {
        return new RefreshRateModeSnapshot(
                0, currentWidth, currentHeight, 60f, Arrays.asList(modes));
    }

    @Test
    public void selectHighest_choosesMaxRefreshAtCurrentResolution() {
        RefreshRateModeSnapshot snapshot = snapshot(
                1080, 2400,
                new RefreshRateMode(1, 1080, 2400, 60f),
                new RefreshRateMode(2, 1080, 2400, 120f),
                new RefreshRateMode(3, 720, 1600, 144f));

        RefreshRateMode selected = RefreshRateModeSelector.select(
                RefreshRatePolicy.highest(), snapshot);

        assertNotNull(selected);
        assertEquals(120f, selected.refreshRateHz(), 0.01f);
        assertEquals(2, selected.modeId());
    }

    @Test
    public void selectHighest_prefersCurrentResolutionOverHigherHzDifferentResolution() {
        RefreshRateModeSnapshot snapshot = snapshot(
                1080, 2400,
                new RefreshRateMode(1, 1080, 2400, 90f),
                new RefreshRateMode(2, 720, 1600, 144f));

        RefreshRateMode selected = RefreshRateModeSelector.select(
                RefreshRatePolicy.highest(), snapshot);

        assertNotNull(selected);
        assertEquals(90f, selected.refreshRateHz(), 0.01f);
        assertEquals(1080, selected.width());
    }

    @Test
    public void selectHighest_whenNoResolutionMatch_returnsNull() {
        RefreshRateModeSnapshot snapshot = snapshot(
                1080, 2400,
                new RefreshRateMode(1, 720, 1600, 90f),
                new RefreshRateMode(2, 720, 1600, 120f));

        RefreshRateMode selected = RefreshRateModeSelector.select(
                RefreshRatePolicy.highest(), snapshot);

        assertNull(selected);
    }

    @Test
    public void selectHighest_doesNotUseDifferentResolutionForTieBreak() {
        RefreshRateModeSnapshot snapshot = snapshot(
                1080, 2400,
                new RefreshRateMode(1, 720, 1600, 120f),
                new RefreshRateMode(2, 1080, 1920, 120f));

        RefreshRateMode selected = RefreshRateModeSelector.select(
                RefreshRatePolicy.highest(), snapshot);

        assertNull(selected);
    }

    @Test
    public void selectTarget_prefersHigherWhenDistanceEqual() {
        RefreshRateModeSnapshot snapshot = snapshot(
                1080, 2400,
                new RefreshRateMode(1, 1080, 2400, 60f),
                new RefreshRateMode(2, 1080, 2400, 120f));

        RefreshRateMode selected = RefreshRateModeSelector.select(
                RefreshRatePolicy.target(90f), snapshot);

        assertNotNull(selected);
        assertEquals(120f, selected.refreshRateHz(), 0.01f);
    }

    @Test
    public void selectTarget_choosesClosestRate() {
        RefreshRateModeSnapshot snapshot = snapshot(
                1080, 2400,
                new RefreshRateMode(1, 1080, 2400, 60f),
                new RefreshRateMode(2, 1080, 2400, 90f),
                new RefreshRateMode(3, 1080, 2400, 120f));

        RefreshRateMode selected = RefreshRateModeSelector.select(
                RefreshRatePolicy.target(100f), snapshot);

        assertNotNull(selected);
        assertEquals(90f, selected.refreshRateHz(), 0.01f);
    }

    @Test
    public void selectAtLeast_choosesLowestEligibleMode() {
        RefreshRateModeSnapshot snapshot = snapshot(
                1080, 2400,
                new RefreshRateMode(1, 1080, 2400, 60f),
                new RefreshRateMode(2, 1080, 2400, 90f),
                new RefreshRateMode(3, 1080, 2400, 120f));

        RefreshRateMode selected = RefreshRateModeSelector.select(
                RefreshRatePolicy.atLeast(90f), snapshot);

        assertNotNull(selected);
        assertEquals(90f, selected.refreshRateHz(), 0.01f);
    }

    @Test
    public void selectAtLeast_whenNoEligibleMode_fallsBackToHighest() {
        RefreshRateModeSnapshot snapshot = snapshot(
                1080, 2400,
                new RefreshRateMode(1, 1080, 2400, 60f));

        RefreshRateMode selected = RefreshRateModeSelector.select(
                RefreshRatePolicy.atLeast(90f), snapshot);

        assertNotNull(selected);
        assertEquals(60f, selected.refreshRateHz(), 0.01f);
    }

    @Test
    public void select_handlesEmptyModeList() {
        RefreshRateModeSnapshot snapshot = snapshot(1080, 2400);

        RefreshRateMode selected = RefreshRateModeSelector.select(
                RefreshRatePolicy.highest(), snapshot);

        assertNull(selected);
    }

    @Test
    public void select_filtersInvalidModes() {
        RefreshRateModeSnapshot snapshot = new RefreshRateModeSnapshot(
                0, 1080, 2400, 60f,
                Arrays.asList(
                        new RefreshRateMode(0, 1080, 2400, 120f),
                        new RefreshRateMode(2, 0, 2400, 120f),
                        new RefreshRateMode(3, 1080, 2400, 120f)));

        RefreshRateMode selected = RefreshRateModeSelector.select(
                RefreshRatePolicy.highest(), snapshot);

        assertNotNull(selected);
        assertEquals(3, selected.modeId());
    }

    @Test
    public void select_systemDefaultReturnsNull() {
        RefreshRateModeSnapshot snapshot = snapshot(
                1080, 2400,
                new RefreshRateMode(1, 1080, 2400, 120f));

        assertNull(RefreshRateModeSelector.select(
                RefreshRatePolicy.systemDefault(), snapshot));
    }

    @Test
    public void hasHighRefreshMode_detectsAboveSixtyHz() {
        RefreshRateModeSnapshot only60 = snapshot(
                1080, 2400,
                new RefreshRateMode(1, 1080, 2400, 60f));
        RefreshRateModeSnapshot with120 = snapshot(
                1080, 2400,
                new RefreshRateMode(1, 1080, 2400, 60f),
                new RefreshRateMode(2, 1080, 2400, 120f));

        assertFalse(RefreshRateModeSelector.hasHighRefreshMode(only60));
        assertTrue(RefreshRateModeSelector.hasHighRefreshMode(with120));
    }

    @Test
    public void hasHighRefreshMode_ignoresDifferentResolution() {
        RefreshRateModeSnapshot snapshot = snapshot(
                1080, 2400,
                new RefreshRateMode(1, 1080, 2400, 60f),
                new RefreshRateMode(2, 720, 1600, 144f));

        assertFalse(RefreshRateModeSelector.hasHighRefreshMode(snapshot));
    }

    @Test
    public void supportedRefreshRates_returnsSortedDistinctRates() {
        RefreshRateModeSnapshot snapshot = snapshot(
                1080, 2400,
                new RefreshRateMode(1, 1080, 2400, 120f),
                new RefreshRateMode(2, 1080, 2400, 60f),
                new RefreshRateMode(3, 720, 1600, 90f));

        List<Float> rates = RefreshRateModeSelector.supportedRefreshRates(snapshot);

        assertEquals(Arrays.asList(60f, 120f), rates);
    }

    @Test
    public void supportedRefreshRates_excludesDifferentResolutionAndNearDuplicates() {
        RefreshRateModeSnapshot snapshot = snapshot(
                1080, 2400,
                new RefreshRateMode(1, 1080, 2400, 60f),
                new RefreshRateMode(2, 1080, 2400, 59.94f),
                new RefreshRateMode(3, 720, 1600, 144f));

        assertEquals(Collections.singletonList(60f),
                RefreshRateModeSelector.supportedRefreshRates(snapshot));
    }

    @Test
    public void supportedRefreshRates_emptyForNoValidModes() {
        RefreshRateModeSnapshot snapshot = new RefreshRateModeSnapshot(
                0, 1080, 2400, 60f, Collections.emptyList());

        assertTrue(RefreshRateModeSelector.supportedRefreshRates(snapshot).isEmpty());
    }
}
