package com.yovo.lib_bluetooth.internal.classic.profiles.state;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtProfileType;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 设备多 Profile 聚合状态快照。
 */

public final class YoCbtProfileAggregateState {
    private final YoCbtProfileType profileType;
    private final YoCbtProfileConnectionState state;
    private final int connectedCount;
    private final long queryTimeMillis;

    public YoCbtProfileAggregateState(@NonNull YoCbtProfileType profileType,
                                      @NonNull YoCbtProfileConnectionState state,
                                      int connectedCount,
                                      long queryTimeMillis) {
        this.profileType = profileType;
        this.state = state;
        this.connectedCount = connectedCount;
        this.queryTimeMillis = queryTimeMillis;
    }

    @NonNull
    public YoCbtProfileType getProfileType() {
        return profileType;
    }

    @NonNull
    public YoCbtProfileConnectionState getState() {
        return state;
    }

    public int getConnectedCount() {
        return connectedCount;
    }

    public long getQueryTimeMillis() {
        return queryTimeMillis;
    }
}
