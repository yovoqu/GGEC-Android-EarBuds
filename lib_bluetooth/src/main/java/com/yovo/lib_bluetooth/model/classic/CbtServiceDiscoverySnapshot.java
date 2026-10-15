package com.yovo.lib_bluetooth.model.classic;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * SDP 服务发现结果快照。
 */
public final class CbtServiceDiscoverySnapshot {

    @NonNull
    private final CbtDeviceId deviceId;
    @NonNull
    private final CbtDeviceSnapshot deviceSnapshot;
    @NonNull
    private final List<UUID> discoveredUuids;
    @NonNull
    private final List<CbtSppEndpoint> sppEndpoints;

    public CbtServiceDiscoverySnapshot(@NonNull CbtDeviceId deviceId,
                                       @NonNull CbtDeviceSnapshot deviceSnapshot,
                                       @NonNull List<UUID> discoveredUuids,
                                       @NonNull List<CbtSppEndpoint> sppEndpoints) {
        this.deviceId = deviceId;
        this.deviceSnapshot = deviceSnapshot;
        this.discoveredUuids = Collections.unmodifiableList(new ArrayList<>(discoveredUuids));
        this.sppEndpoints = Collections.unmodifiableList(new ArrayList<>(sppEndpoints));
    }

    @NonNull
    public CbtDeviceId deviceId() {
        return deviceId;
    }

    @NonNull
    public CbtDeviceSnapshot deviceSnapshot() {
        return deviceSnapshot;
    }

    @NonNull
    public List<UUID> discoveredUuids() {
        return discoveredUuids;
    }

    @NonNull
    public List<CbtSppEndpoint> sppEndpoints() {
        return sppEndpoints;
    }
}
