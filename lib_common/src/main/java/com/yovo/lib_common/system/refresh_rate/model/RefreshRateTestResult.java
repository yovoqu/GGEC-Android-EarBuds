package com.yovo.lib_common.system.refresh_rate.model;

import androidx.annotation.NonNull;

import java.util.Objects;

/** Final evidence produced by one request/settle/sample/restore refresh-rate test. */
public record RefreshRateTestResult(
        @NonNull RefreshRateResult request,
        @NonNull RefreshRateFrameSample frameSample,
        boolean requestedCadenceObserved,
        boolean restored) {

    public RefreshRateTestResult {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(frameSample, "frameSample");
    }

    public float requestedHz() {
        return request.selectedRefreshRateHz();
    }

    public float measuredHz() {
        return frameSample.estimatedRefreshRateHz();
    }
}
