package com.yovo.lib_common.system.refresh_rate.diagnostics;

import com.yovo.lib_common.system.refresh_rate.model.RefreshRateFrameSample;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RefreshRateFrameSampleTest {

    @Test
    public void estimatedRefreshRateUsesMedianInterval() {
        RefreshRateFrameSample sample = new RefreshRateFrameSample(
                60, 8_333_333L, 9_000_000L, 8_000_000L, 16_000_000L);

        assertEquals(120f, sample.estimatedRefreshRateHz(), 0.1f);
        assertEquals(60, sample.sampleCount());
        assertTrue(sample.toString().contains("120.00Hz"));
    }

    @Test
    public void zeroMedianProducesZeroRate() {
        RefreshRateFrameSample sample = new RefreshRateFrameSample(0, 0, 0, 0, 0);
        assertEquals(0f, sample.estimatedRefreshRateHz(), 0f);
    }

    @Test
    public void rejectsInconsistentIntervals() {
        assertThrows(IllegalArgumentException.class,
                () -> new RefreshRateFrameSample(10, 9L, 8L, 7L, 10L));
        assertThrows(IllegalArgumentException.class,
                () -> new RefreshRateFrameSample(-1, 0L, 0L, 0L, 0L));
    }
}
