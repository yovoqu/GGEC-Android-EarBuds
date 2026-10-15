package com.yovo.lib_common.system.screen_on.internal;

import android.app.Activity;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;

/**
 * Applies or clears {@link WindowManager.LayoutParams#FLAG_KEEP_SCREEN_ON} on an Activity window.
 */
public final class ScreenOnWindowApplier {

    private ScreenOnWindowApplier() {
    }

    public static void apply(@NonNull Activity activity, boolean keepOn) {
        Window window = activity.getWindow();
        if (keepOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    public static void clear(@NonNull Activity activity) {
        apply(activity, false);
    }

    public static boolean isApplied(@NonNull Activity activity) {
        return (activity.getWindow().getAttributes().flags
                & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0;
    }
}
