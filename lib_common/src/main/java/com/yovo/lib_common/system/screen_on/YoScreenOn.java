package com.yovo.lib_common.system.screen_on;

import com.yovo.lib_common.system.screen_on.internal.ScreenOnCore;

import android.app.Activity;

import androidx.annotation.NonNull;

/**
 * 亮屏控制对外门面。
 */
public final class YoScreenOn {

    private YoScreenOn() {
    }

    @NonNull
    public static Controller bind(@NonNull Activity activity) {
        return ScreenOnCore.bind(activity);
    }

    public static void apply(@NonNull Activity activity, boolean keepOn) {
        ScreenOnCore.apply(activity, keepOn);
    }

    public static void clear(@NonNull Activity activity) {
        ScreenOnCore.clear(activity);
    }

    public static boolean isApplied(@NonNull Activity activity) {
        return ScreenOnCore.isApplied(activity);
    }

    public interface Controller {
        void attach();
        void detach();
        void onResume();
        void onWindowFocusChanged(boolean hasFocus);
        boolean isAttached();
        boolean isApplied();
    }
}
