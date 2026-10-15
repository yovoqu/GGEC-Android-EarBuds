package com.yovo.lib_bluetooth.model.classic;

import androidx.annotation.NonNull;

/**
 * Profile 聚合状态。
 */
public final class CbtProfileAggregateState {

    @NonNull
    private final CbtProfileType profileType;
    @NonNull
    private final CbtProfileConnectionState state;
    private final int connectedCount;
    private final long updatedAtMillis;

    public CbtProfileAggregateState(@NonNull CbtProfileType profileType,
                                    @NonNull CbtProfileConnectionState state,
                                    int connectedCount,
                                    long updatedAtMillis) {
        this.profileType = profileType;
        this.state = state;
        this.connectedCount = connectedCount;
        this.updatedAtMillis = updatedAtMillis;
    }

    @NonNull
    public CbtProfileType profileType() {
        return profileType;
    }

    @NonNull
    public CbtProfileConnectionState state() {
        return state;
    }

    public int connectedCount() {
        return connectedCount;
    }

    public long updatedAtMillis() {
        return updatedAtMillis;
    }
}
