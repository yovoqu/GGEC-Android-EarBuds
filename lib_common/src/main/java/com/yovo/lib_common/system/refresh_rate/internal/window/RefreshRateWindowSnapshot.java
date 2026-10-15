package com.yovo.lib_common.system.refresh_rate.internal.window;

import androidx.annotation.Nullable;

/**
 * Window attribute values after apply or clear.
 */
public final class RefreshRateWindowSnapshot {

    private final int preferredDisplayModeId;
    private final float preferredRefreshRateHz;

    public RefreshRateWindowSnapshot(int preferredDisplayModeId, float preferredRefreshRateHz) {
        this.preferredDisplayModeId = preferredDisplayModeId;
        this.preferredRefreshRateHz = preferredRefreshRateHz;
    }

    public int preferredDisplayModeId() {
        return preferredDisplayModeId;
    }

    public float preferredRefreshRateHz() {
        return preferredRefreshRateHz;
    }

    public boolean isCleared() {
        return preferredDisplayModeId == 0 && preferredRefreshRateHz <= 0f;
    }
}
