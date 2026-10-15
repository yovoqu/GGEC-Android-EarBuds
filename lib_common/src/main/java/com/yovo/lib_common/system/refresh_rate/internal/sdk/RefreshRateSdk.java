package com.yovo.lib_common.system.refresh_rate.internal.sdk;

import android.os.Build;

/**
 * SDK capability gates for refresh-rate APIs.
 */
public final class RefreshRateSdk {

    private RefreshRateSdk() {
    }

    public static boolean supportsDisplayModes() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
    }

    public static boolean supportsSurfaceFrameRate() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;
    }

    public static boolean supportsThermalStatus() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
    }

    public static boolean supportsActivityPostResumed() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
    }
}
