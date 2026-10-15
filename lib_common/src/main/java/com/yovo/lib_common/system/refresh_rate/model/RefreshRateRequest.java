package com.yovo.lib_common.system.refresh_rate.model;

import androidx.annotation.NonNull;

import com.yovo.lib_common.system.refresh_rate.model.RefreshRatePolicy;

/**
 * Immutable request contract passed through the apply pipeline.
 */
public final class RefreshRateRequest {

    private final RefreshRatePolicy policy;

    public RefreshRateRequest(@NonNull RefreshRatePolicy policy) {
        this.policy = policy;
    }

    @NonNull
    public RefreshRatePolicy policy() {
        return policy;
    }

    @Override
    public String toString() {
        return "RefreshRateRequest{" + policy + "}";
    }
}
