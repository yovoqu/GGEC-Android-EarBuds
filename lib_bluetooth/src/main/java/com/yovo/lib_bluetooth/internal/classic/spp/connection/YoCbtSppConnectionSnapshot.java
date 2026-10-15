package com.yovo.lib_bluetooth.internal.classic.spp.connection;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtFailure;
import com.yovo.lib_bluetooth.internal.classic.spp.endpoint.YoCbtSppEndpoint;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description SPP 连接某一时刻的状态快照。
 */

public final class YoCbtSppConnectionSnapshot {
    private final YoCbtSppConnectionId connectionId;
    private final YoCbtDeviceId deviceId;
    private final YoCbtSppEndpoint endpoint;
    private final YoCbtSppConnectionState state;
    private final long updatedAtMillis;
    private final YoCbtFailure failure;
    private final int resolvedChannel;

    public YoCbtSppConnectionSnapshot(@NonNull YoCbtSppConnectionId connectionId,
                                      @NonNull YoCbtDeviceId deviceId,
                                      @NonNull YoCbtSppEndpoint endpoint,
                                      @NonNull YoCbtSppConnectionState state,
                                      long updatedAtMillis,
                                      @Nullable YoCbtFailure failure,
                                      int resolvedChannel) {
        this.connectionId = connectionId;
        this.deviceId = deviceId;
        this.endpoint = endpoint;
        this.state = state;
        this.updatedAtMillis = updatedAtMillis;
        this.failure = failure;
        this.resolvedChannel = resolvedChannel;
    }

    @NonNull
    public YoCbtSppConnectionId getConnectionId() {
        return connectionId;
    }

    @NonNull
    public YoCbtDeviceId getDeviceId() {
        return deviceId;
    }

    @NonNull
    public YoCbtSppEndpoint getEndpoint() {
        return endpoint;
    }

    @NonNull
    public YoCbtSppConnectionState getState() {
        return state;
    }

    public long getUpdatedAtMillis() {
        return updatedAtMillis;
    }

    @Nullable
    public YoCbtFailure getFailure() {
        return failure;
    }

    /**
     * 该连接实际占用的物理 RFCOMM 通道号；未知时返回 -1。
     * 通道端点连接立即可知；UUID 端点连接在成功后通过 SDP/mPort 解析得到。
     */
    public int getResolvedChannel() {
        return resolvedChannel;
    }
}
