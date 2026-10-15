package com.yovo.lib_common.system.refresh_rate.internal.core;

import android.app.Activity;

import androidx.annotation.NonNull;

import com.yovo.lib_common.system.refresh_rate.YoRefreshRate;
import com.yovo.lib_common.system.refresh_rate.model.RefreshRateRequest;
import com.yovo.lib_common.system.refresh_rate.model.RefreshRateResult;
import com.yovo.lib_common.system.refresh_rate.model.RefreshRatePolicy;
import com.yovo.lib_common.system.refresh_rate.internal.window.RefreshRateWindowApplier;

import java.lang.ref.WeakReference;

/**
 * Per-Activity controller for explicit refresh-rate control.
 */
public final class RefreshRateControllerImpl implements YoRefreshRate.Controller {

    @NonNull
    private final WeakReference<Activity> activityRef;
    @NonNull
    private volatile RefreshRatePolicy policy;

    RefreshRateControllerImpl(@NonNull Activity activity,
                                @NonNull RefreshRatePolicy policy) {
        this.activityRef = new WeakReference<>(activity);
        this.policy = policy;
    }

    @NonNull
    public RefreshRatePolicy policy() {
        return policy;
    }

    public void setPolicy(@NonNull RefreshRatePolicy policy) {
        this.policy = policy;
    }

    @NonNull
    public RefreshRateResult apply() {
        Activity activity = activityRef.get();
        if (activity == null) {
            return RefreshRateResult.noDisplay(policy);
        }
        return RefreshRateWindowApplier.apply(activity, new RefreshRateRequest(policy));
    }

    @NonNull
    public RefreshRateResult apply(@NonNull RefreshRatePolicy policy) {
        setPolicy(policy);
        return apply();
    }

    @NonNull
    public RefreshRateResult clear() {
        Activity activity = activityRef.get();
        if (activity == null) {
            return RefreshRateResult.noDisplay(RefreshRatePolicy.systemDefault());
        }
        return RefreshRateWindowApplier.clear(activity);
    }

    /**
     * Optional hook when a base Activity wires {@code onWindowFocusChanged}.
     */
    public void onWindowFocusChanged(boolean hasFocus) {
        if (!hasFocus) {
            return;
        }
        apply();
    }
}
