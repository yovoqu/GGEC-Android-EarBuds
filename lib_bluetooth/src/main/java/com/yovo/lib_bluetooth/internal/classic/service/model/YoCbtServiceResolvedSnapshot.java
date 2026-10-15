package com.yovo.lib_bluetooth.internal.classic.service.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.classic.device.snapshot.YoCbtDeviceSnapshot;
import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtProfileType;
import com.yovo.lib_bluetooth.internal.classic.spp.endpoint.YoCbtSppEndpoint;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 传播给观察者和快照的、已完全富化的服务发现输出。
 */
public final class YoCbtServiceResolvedSnapshot {

    @NonNull
    private final YoCbtDeviceSnapshot deviceSnapshot;
    @NonNull
    private final YoCbtSdpRecord sdpRecord;
    @NonNull
    private final Set<YoCbtProfileType> profileCapabilities;
    @NonNull
    private final List<YoCbtSppEndpoint> sppEndpoints;
    @Nullable
    private final UUID selectedUuid;

    public YoCbtServiceResolvedSnapshot(@NonNull YoCbtDeviceSnapshot deviceSnapshot,
                                        @NonNull YoCbtSdpRecord sdpRecord,
                                        @NonNull Set<YoCbtProfileType> profileCapabilities,
                                        @NonNull List<YoCbtSppEndpoint> sppEndpoints,
                                        @Nullable UUID selectedUuid) {
        this.deviceSnapshot = Objects.requireNonNull(deviceSnapshot, "deviceSnapshot must not be null");
        this.sdpRecord = Objects.requireNonNull(sdpRecord, "sdpRecord must not be null");
        this.profileCapabilities = Collections.unmodifiableSet(profileCapabilities);
        this.sppEndpoints = Collections.unmodifiableList(sppEndpoints);
        this.selectedUuid = selectedUuid;
    }

    @NonNull
    public YoCbtDeviceSnapshot getDeviceSnapshot() {
        return deviceSnapshot;
    }

    @NonNull
    public YoCbtSdpRecord getSdpRecord() {
        return sdpRecord;
    }

    @NonNull
    public Set<YoCbtProfileType> getProfileCapabilities() {
        return profileCapabilities;
    }

    @NonNull
    public List<YoCbtSppEndpoint> getSppEndpoints() {
        return sppEndpoints;
    }

    @Nullable
    public UUID getSelectedUuid() {
        return selectedUuid;
    }
}
