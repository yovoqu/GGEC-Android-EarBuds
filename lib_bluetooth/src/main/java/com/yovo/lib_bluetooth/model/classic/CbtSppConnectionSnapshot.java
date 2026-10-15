package com.yovo.lib_bluetooth.model.classic;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * SPP 连接快照。failure 与 resolvedChannel 是会话事实的一部分，不是旁路事件。
 */
public final class CbtSppConnectionSnapshot {

    @NonNull
    private final CbtSppConnectionId connectionId;
    @NonNull
    private final CbtDeviceId deviceId;
    @NonNull
    private final CbtSppConnectionState state;
    @NonNull
    private final CbtSppEndpoint endpoint;
    @Nullable
    private final CbtFailure failure;
    private final int resolvedChannel;

    public CbtSppConnectionSnapshot(@NonNull CbtSppConnectionId connectionId,
                                    @NonNull CbtDeviceId deviceId,
                                    @NonNull CbtSppConnectionState state,
                                    @NonNull CbtSppEndpoint endpoint) {
        this(connectionId, deviceId, state, endpoint, null, -1);
    }

    public CbtSppConnectionSnapshot(@NonNull CbtSppConnectionId connectionId,
                                    @NonNull CbtDeviceId deviceId,
                                    @NonNull CbtSppConnectionState state,
                                    @NonNull CbtSppEndpoint endpoint,
                                    @Nullable CbtFailure failure,
                                    int resolvedChannel) {
        this.connectionId = connectionId;
        this.deviceId = deviceId;
        this.state = state;
        this.endpoint = endpoint;
        this.failure = failure;
        this.resolvedChannel = resolvedChannel;
    }

    @NonNull
    public CbtSppConnectionId connectionId() {
        return connectionId;
    }

    @NonNull
    public CbtDeviceId deviceId() {
        return deviceId;
    }

    @NonNull
    public CbtSppConnectionState state() {
        return state;
    }

    @NonNull
    public CbtSppEndpoint endpoint() {
        return endpoint;
    }

    @Nullable
    public CbtFailure failure() {
        return failure;
    }

    public int resolvedChannel() {
        return resolvedChannel;
    }
}
