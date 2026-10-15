package com.yovo.lib_common.system.refresh_rate.internal.core;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_common.system.refresh_rate.model.RefreshRateRequest;
import com.yovo.lib_common.system.refresh_rate.model.RefreshRatePolicy;
import com.yovo.lib_common.system.refresh_rate.internal.window.RefreshRateWindowApplier;

/**
 * App-wide Activity lifecycle integration for high refresh-rate requests.
 */
public final class RefreshRateLifecycleCallbacks implements Application.ActivityLifecycleCallbacks {

    @NonNull
    private volatile RefreshRatePolicy policy;

    public RefreshRateLifecycleCallbacks(@NonNull RefreshRatePolicy policy) {
        this.policy = policy;
    }

    public void setPolicy(@NonNull RefreshRatePolicy policy) {
        this.policy = policy;
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
        // Apply on resume, when the window is attached and the Activity is visible.
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        // no-op
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        applyIfPossible(activity);
    }

    @Override
    public void onActivityPostResumed(@NonNull Activity activity) {
        // onActivityResumed already applied the preference. Avoid duplicate requests.
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
        // no-op
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        // no-op
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
        // no-op
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
        // no-op
    }

    private void applyIfPossible(@NonNull Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        RefreshRateWindowApplier.apply(activity, new RefreshRateRequest(policy));
    }
}
