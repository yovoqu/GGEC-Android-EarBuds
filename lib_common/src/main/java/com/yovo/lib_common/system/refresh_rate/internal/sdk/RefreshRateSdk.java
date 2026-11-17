package com.yovo.lib_common.system.refresh_rate.internal.sdk;

import android.os.Build;

/**
 * SDK capability gates for refresh-rate APIs.
 *
 * <p>minSdk 24 起恒定可用的能力（如 {@code Display.getSupportedModes}）不设开关，
 * 调用点直接使用该 API。</p>
 */
public final class RefreshRateSdk {

    private RefreshRateSdk() {
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
