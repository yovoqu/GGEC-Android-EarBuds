package com.yovo.lib_common.system.refresh_rate.model;

import androidx.annotation.NonNull;

import java.util.Locale;

/**
 * Display mode fields captured for diagnostics output.
 */
public final class RefreshRateSystemModeSnapshot {

    private final int modeId;
    private final int width;
    private final int height;
    private final float refreshRateHz;

    public RefreshRateSystemModeSnapshot(int modeId, int width, int height, float refreshRateHz) {
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

    @NonNull
    @Override
    public String toString() {
        return String.format(Locale.US,
                "Mode{id=%d, %dx%d, %.2fHz}", modeId, width, height, refreshRateHz);
    }
}
