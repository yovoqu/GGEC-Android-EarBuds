package com.yovo.lib_common.system.refresh_rate.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Outcome of applying or clearing a refresh-rate request on a window.
 */
public final class RefreshRateResult {

    @NonNull
    private final RefreshRatePolicy requestedPolicy;
    private final int selectedModeId;
    private final float selectedRefreshRateHz;
    private final float observedRefreshRateHz;
    @NonNull
    private final RefreshRateStatus status;
    @NonNull
    private final RefreshRateDiagnostics diagnostics;

    public RefreshRateResult(@NonNull RefreshRatePolicy requestedPolicy,
                             int selectedModeId,
                             float selectedRefreshRateHz,
                             float observedRefreshRateHz,
                             @NonNull RefreshRateStatus status,
                             @NonNull RefreshRateDiagnostics diagnostics) {
        this.requestedPolicy = requestedPolicy;
        this.selectedModeId = selectedModeId;
        this.selectedRefreshRateHz = selectedRefreshRateHz;
        this.observedRefreshRateHz = observedRefreshRateHz;
        this.status = status;
        this.diagnostics = diagnostics;
    }

    @NonNull
    public RefreshRatePolicy requestedPolicy() {
        return requestedPolicy;
    }

    public int selectedModeId() {
        return selectedModeId;
    }

    public float selectedRefreshRateHz() {
        return selectedRefreshRateHz;
    }

    public float observedRefreshRateHz() {
        return observedRefreshRateHz;
    }

    @NonNull
    public RefreshRateStatus status() {
        return status;
    }

    @NonNull
    public RefreshRateDiagnostics diagnostics() {
        return diagnostics;
    }

    public boolean isApplied() {
        return status == RefreshRateStatus.APPLIED;
    }

    /**
     * Returns whether the platform accepted a window preference write.
     *
     * <p>This is intentionally weaker than {@link #isApplied()}: Android refresh-rate APIs
     * express a preference, and vendor policy may defer or constrain the actual cadence.</p>
     */
    public boolean isRequestAccepted() {
        return status == RefreshRateStatus.APPLIED
                || status == RefreshRateStatus.REQUESTED
                || status == RefreshRateStatus.APPLIED_BUT_NOT_OBSERVED;
    }

    /** Returns whether the observed display rate matches the selected same-resolution mode. */
    public boolean isObservedAtSelectedRate() {
        return status == RefreshRateStatus.APPLIED;
    }

    @NonNull
    public static RefreshRateResult error(@NonNull RefreshRatePolicy policy,
                                          @NonNull RefreshRateDiagnostics diagnostics) {
        return new RefreshRateResult(
                policy, 0, 0f, 0f, RefreshRateStatus.ERROR, diagnostics);
    }

    @NonNull
    public static RefreshRateResult noDisplay(@NonNull RefreshRatePolicy policy) {
        return new RefreshRateResult(
                policy, 0, 0f, 0f, RefreshRateStatus.NO_DISPLAY, RefreshRateDiagnostics.empty());
    }

    @Override
    public String toString() {
        return "RefreshRateResult{policy=" + requestedPolicy
                + ", selectedModeId=" + selectedModeId
                + ", selectedHz=" + selectedRefreshRateHz
                + ", observedHz=" + observedRefreshRateHz
                + ", status=" + status
                + ", diagnostics=" + diagnostics + '}';
    }
}
