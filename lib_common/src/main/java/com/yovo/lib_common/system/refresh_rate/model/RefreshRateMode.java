package com.yovo.lib_common.system.refresh_rate.model;

import androidx.annotation.NonNull;

import java.util.Locale;

/**
 * Immutable snapshot of one supported display mode.
 */
public final class RefreshRateMode {

    /** Refresh rates within this delta are treated as equal. */
    public static final float REFRESH_RATE_EPSILON = 0.5f;

    /** Rates above this threshold are considered high refresh for this module. */
    public static final float HIGH_REFRESH_THRESHOLD_HZ = 60f + REFRESH_RATE_EPSILON;

    private final int modeId;
    private final int width;
    private final int height;
    private final float refreshRateHz;

    public RefreshRateMode(int modeId, int width, int height, float refreshRateHz) {
        this.modeId = modeId;
        this.width = width;
        this.height = height;
        this.refreshRateHz = refreshRateHz;
    }

    public int modeId() {
        return modeId;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public float refreshRateHz() {
        return refreshRateHz;
    }

    public long pixelArea() {
        return (long) width * (long) height;
    }

    public boolean matchesResolution(int currentWidth, int currentHeight) {
        return width == currentWidth && height == currentHeight;
    }

    public boolean isValid() {
        return modeId > 0 && width > 0 && height > 0 && refreshRateHz > 0f;
    }

    public boolean isHighRefresh() {
        return refreshRateHz > HIGH_REFRESH_THRESHOLD_HZ;
    }

    @NonNull
    @Override
    public String toString() {
        return String.format(Locale.US,
                "RefreshRateMode{id=%d, %dx%d, %.2fHz}",
                modeId, width, height, refreshRateHz);
    }
}
