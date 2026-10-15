package com.yovo.lib_common.system.refresh_rate.internal.display;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_common.system.refresh_rate.model.RefreshRateMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable snapshot of display capabilities at resolve time.
 */
public final class RefreshRateModeSnapshot {

    private final int displayId;
    private final int currentWidth;
    private final int currentHeight;
    private final float currentRefreshRateHz;
    @NonNull
    private final List<RefreshRateMode> supportedModes;

    public RefreshRateModeSnapshot(int displayId,
                                   int currentWidth,
                                   int currentHeight,
                                   float currentRefreshRateHz,
                                   @NonNull List<RefreshRateMode> supportedModes) {
        this.displayId = displayId;
        this.currentWidth = currentWidth;
        this.currentHeight = currentHeight;
        this.currentRefreshRateHz = currentRefreshRateHz;
        this.supportedModes = Collections.unmodifiableList(new ArrayList<>(supportedModes));
    }

    public int displayId() {
        return displayId;
    }

    public int currentWidth() {
        return currentWidth;
    }

    public int currentHeight() {
        return currentHeight;
    }

    public float currentRefreshRateHz() {
        return currentRefreshRateHz;
    }

    @NonNull
    public List<RefreshRateMode> supportedModes() {
        return supportedModes;
    }

    public boolean hasSupportedModes() {
        return !supportedModes.isEmpty();
    }

    @Nullable
    public RefreshRateMode currentModeMatch() {
        for (RefreshRateMode mode : supportedModes) {
            if (mode.matchesResolution(currentWidth, currentHeight)
                    && Math.abs(mode.refreshRateHz() - currentRefreshRateHz)
                    <= RefreshRateMode.REFRESH_RATE_EPSILON) {
                return mode;
            }
        }
        return null;
    }
}
