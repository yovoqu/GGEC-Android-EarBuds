package com.yovo.lib_common.system.screen_on.internal;

import android.app.Activity;

import androidx.annotation.NonNull;

import com.yovo.lib_common.system.screen_on.YoScreenOn;

import java.lang.ref.WeakReference;

/**
 * Per-Activity controller that keeps the window lit while attached.
 */
public final class ScreenOnControllerImpl implements YoScreenOn.Controller {

    @NonNull
    private final WeakReference<Activity> activityRef;
    private volatile boolean attached;
    private boolean wasAppliedBeforeAttach;

    public ScreenOnControllerImpl(@NonNull Activity activity) {
        this.activityRef = new WeakReference<>(activity);
    }

    public void attach() {
        if (attached) {
            return;
        }
        attached = true;
        Activity activity = activityRef.get();
        wasAppliedBeforeAttach = activity != null
                && ScreenOnWindowApplier.isApplied(activity);
        apply(true);
    }

    public void detach() {
        if (!attached) {
            return;
        }
        attached = false;
        Activity activity = activityRef.get();
        if (activity != null && !wasAppliedBeforeAttach) {
            ScreenOnWindowApplier.clear(activity);
        }
    }

    /**
     * Re-applies the flag after resume in case the system cleared it.
     */
    public void onResume() {
        if (attached) {
            apply(true);
        }
    }

    /**
     * Optional hook when a base Activity wires {@code onWindowFocusChanged}.
     */
    public void onWindowFocusChanged(boolean hasFocus) {
        if (hasFocus && attached) {
            apply(true);
        }
    }

    public boolean isAttached() {
        return attached;
    }

    public boolean isApplied() {
        Activity activity = activityRef.get();
        return activity != null && ScreenOnWindowApplier.isApplied(activity);
    }

    private void apply(boolean keepOn) {
        Activity activity = activityRef.get();
        if (activity != null) {
            ScreenOnWindowApplier.apply(activity, keepOn);
        }
    }
}
