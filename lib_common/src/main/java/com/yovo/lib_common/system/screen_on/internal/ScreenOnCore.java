package com.yovo.lib_common.system.screen_on.internal;

import com.yovo.lib_common.system.screen_on.YoScreenOn;
import com.yovo.lib_common.log.YoLog;

import android.app.Activity;

import androidx.annotation.NonNull;

/**
 * Public facade for keeping an Activity window lit while it is visible.
 *
 * <p>Typical integration: bind in {@code onCreate}, {@code attach} in {@code onStart},
 * {@code onResume} to re-apply, {@code detach} in {@code onDestroy}.</p>
 * <pre>{@code
 * ScreenOnWindowController controller = ScreenOnCore.bind(activity);
 * // onStart: controller.attach()
 * // onResume: controller.onResume()
 * // onDestroy: controller.detach()
 * }</pre>
 */
public final class ScreenOnCore {

    private static final String TAG = "ScreenOnCore";

    private ScreenOnCore() {
    }

    @NonNull
    public static YoScreenOn.Controller bind(@NonNull Activity activity) {
        return new ScreenOnControllerImpl(activity);
    }

    public static void apply(@NonNull Activity activity, boolean keepOn) {
        YoLog.d(TAG, "应用常亮: " + keepOn
                + " @ " + activity.getClass().getSimpleName());
        ScreenOnWindowApplier.apply(activity, keepOn);
    }

    public static void clear(@NonNull Activity activity) {
        YoLog.d(TAG, "清除常亮 @ " + activity.getClass().getSimpleName());
        ScreenOnWindowApplier.clear(activity);
    }

    public static boolean isApplied(@NonNull Activity activity) {
        return ScreenOnWindowApplier.isApplied(activity);
    }
}
