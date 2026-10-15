package com.yovo.lib_common.system.refresh_rate.model;

import com.yovo.lib_common.system.refresh_rate.model.RefreshRateDiagnostics;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_common.system.refresh_rate.model.RefreshRatePolicy;

import org.junit.Test;

public class RefreshRateResultTest {

    @Test
    public void appliedRequiresObservedSelectedRate() {
        assertTrue(result(RefreshRateStatus.APPLIED).isApplied());
        assertTrue(result(RefreshRateStatus.APPLIED).isRequestAccepted());
        assertTrue(result(RefreshRateStatus.APPLIED).isObservedAtSelectedRate());

        assertFalse(result(RefreshRateStatus.REQUESTED).isApplied());
        assertTrue(result(RefreshRateStatus.REQUESTED).isRequestAccepted());
        assertFalse(result(RefreshRateStatus.REQUESTED).isObservedAtSelectedRate());

        assertFalse(result(RefreshRateStatus.APPLIED_BUT_NOT_OBSERVED).isApplied());
        assertTrue(result(RefreshRateStatus.APPLIED_BUT_NOT_OBSERVED).isRequestAccepted());
        assertFalse(result(RefreshRateStatus.APPLIED_BUT_NOT_OBSERVED)
                .isObservedAtSelectedRate());
    }

    @Test
    public void unavailableModeIsNotReportedAsAcceptedOrApplied() {
        RefreshRateResult unavailable = result(RefreshRateStatus.NO_HIGH_REFRESH_MODE);

        assertFalse(unavailable.isApplied());
        assertFalse(unavailable.isRequestAccepted());
        assertFalse(unavailable.isObservedAtSelectedRate());
    }

    private static RefreshRateResult result(RefreshRateStatus status) {
        return new RefreshRateResult(
                RefreshRatePolicy.target(120f),
                1,
                120f,
                status == RefreshRateStatus.APPLIED ? 120f : 60f,
                status,
                RefreshRateDiagnostics.empty());
    }
}
