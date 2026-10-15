package com.yovo.lib_common.system.refresh_rate.internal.math;

import com.yovo.lib_common.system.refresh_rate.model.RefreshRateMode;

/**
 * Small numeric helpers for refresh-rate comparisons. Not for app-layer use.
 */
public final class RefreshRateMath {

    /** Refresh rates within this delta are treated as equal. */
    public static final float REFRESH_RATE_EPSILON = RefreshRateMode.REFRESH_RATE_EPSILON;

    /** Rates above this threshold are considered high refresh for this module. */
    public static final float HIGH_REFRESH_THRESHOLD_HZ = RefreshRateMode.HIGH_REFRESH_THRESHOLD_HZ;

    private RefreshRateMath() {
    }

    public static boolean nearlyEqual(float a, float b) {
        return Math.abs(a - b) <= REFRESH_RATE_EPSILON;
    }

    public static boolean isHighRefresh(float refreshRateHz) {
        return refreshRateHz > HIGH_REFRESH_THRESHOLD_HZ;
    }

    public static float distance(float targetHz, float refreshRateHz) {
        return Math.abs(targetHz - refreshRateHz);
    }

    public static boolean matchesResolution(int modeWidth, int modeHeight,
                                            int currentWidth, int currentHeight) {
        return modeWidth == currentWidth && modeHeight == currentHeight;
    }

    public static long pixelArea(int width, int height) {
        return (long) width * (long) height;
    }
}
