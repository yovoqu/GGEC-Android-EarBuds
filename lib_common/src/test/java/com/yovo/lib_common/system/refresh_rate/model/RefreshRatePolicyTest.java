package com.yovo.lib_common.system.refresh_rate.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RefreshRatePolicyTest {

    @Test(expected = IllegalArgumentException.class)
    public void target_rejectsNonPositiveHz() {
        RefreshRatePolicy.target(0f);
    }

    @Test(expected = IllegalArgumentException.class)
    public void atLeast_rejectsNonPositiveHz() {
        RefreshRatePolicy.atLeast(-1f);
    }

    @Test(expected = IllegalArgumentException.class)
    public void target_rejectsNanHz() {
        RefreshRatePolicy.target(Float.NaN);
    }

    @Test(expected = IllegalArgumentException.class)
    public void atLeast_rejectsInfiniteHz() {
        RefreshRatePolicy.atLeast(Float.POSITIVE_INFINITY);
    }

    @Test
    public void factoriesExposeExpectedTypes() {
        assertEquals(RefreshRatePolicyType.HIGHEST, RefreshRatePolicy.highest().type());
        assertEquals(RefreshRatePolicyType.TARGET, RefreshRatePolicy.target(90f).type());
        assertEquals(RefreshRatePolicyType.AT_LEAST, RefreshRatePolicy.atLeast(90f).type());
        assertEquals(RefreshRatePolicyType.SYSTEM_DEFAULT,
                RefreshRatePolicy.systemDefault().type());
    }

    @Test
    public void targetStoresRequestedHz() {
        assertEquals(90f, RefreshRatePolicy.target(90f).targetHz(), 0.01f);
    }

    @Test
    public void toStringContainsPolicySummary() {
        assertTrue(RefreshRatePolicy.highest().toString().contains("highest"));
        assertTrue(RefreshRatePolicy.target(120f).toString().contains("120"));
    }
}
