package com.yovo.lib_bluetooth.internal.classic.device.snapshot;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.device.capability.YoCbtDeviceCapability;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceAlias;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceClass;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceIdentity;
import com.yovo.lib_bluetooth.internal.classic.link.YoCbtAclLinkState;
import com.yovo.lib_bluetooth.internal.classic.pairing.YoCbtBondState;
import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtCapabilitySource;
import com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtDeviceProfileState;
import com.yovo.lib_bluetooth.internal.classic.profiles.a2dp.YoCbtA2dpDeviceState;
import com.yovo.lib_bluetooth.internal.classic.profiles.headset.YoCbtHeadsetDeviceState;
import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtProfileType;
import com.yovo.lib_bluetooth.internal.classic.service.model.YoCbtSdpRecord;
import com.yovo.lib_bluetooth.internal.classic.spp.connection.YoCbtSppConnectionSnapshot;
import com.yovo.lib_bluetooth.internal.classic.spp.endpoint.YoCbtSppEndpoint;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 设备全量快照，聚合身份、配对、ACL、Profile 状态、SDP 与 SPP 连接等信息。
 */

public final class YoCbtDeviceSnapshot {
    public static final int RSSI_UNAVAILABLE = Short.MIN_VALUE;
    private static final int MIN_CLASSIC_RSSI = -127;
    private static final int MAX_CLASSIC_RSSI = 20;

    private final YoCbtDeviceIdentity identity;
    private final YoCbtDeviceAlias alias;
    private final YoCbtDeviceClass deviceClass;
    private final YoCbtDeviceCapability capability;
    private final YoCbtBondState bondState;
    private final YoCbtAclLinkState aclLinkState;
    private final List<UUID> discoveredUuids;
    private final Map<YoCbtProfileType, YoCbtDeviceProfileState> profileStates;
    private final YoCbtA2dpDeviceState a2dpState;
    private final YoCbtHeadsetDeviceState headsetState;
    private final List<YoCbtSppConnectionSnapshot> sppConnections;
    private final int rssi;
    private final long lastSeenMillis;
    private final long lastResolvedMillis;
    private final long lastUpdatedMillis;
    private final YoCbtCapabilitySource source;
    @Nullable
    private final YoCbtSdpRecord sdpRecord;
    @NonNull
    private final List<YoCbtSppEndpoint> sdpSppEndpoints;

    public YoCbtDeviceSnapshot(@NonNull YoCbtDeviceIdentity identity,
                               @NonNull YoCbtBondState bondState,
                               @NonNull YoCbtAclLinkState aclLinkState,
                               @NonNull List<UUID> discoveredUuids,
                               @NonNull Map<YoCbtProfileType, YoCbtDeviceProfileState> profileStates,
                               @NonNull List<YoCbtSppConnectionSnapshot> sppConnections,
                               int rssi,
                               long lastSeenMillis,
                               long lastUpdatedMillis) {
        this(identity, identity.getAlias(), YoCbtDeviceClass.unknown(), YoCbtDeviceCapability.empty(),
                bondState, aclLinkState, discoveredUuids, profileStates, null, null, sppConnections, rssi,
                lastSeenMillis, 0L, lastUpdatedMillis, YoCbtCapabilitySource.UNKNOWN,
                null, Collections.<YoCbtSppEndpoint>emptyList());
    }

    public YoCbtDeviceSnapshot(@NonNull YoCbtDeviceIdentity identity,
                               @NonNull YoCbtDeviceAlias alias,
                               @NonNull YoCbtDeviceClass deviceClass,
                               @NonNull YoCbtDeviceCapability capability,
                               @NonNull YoCbtBondState bondState,
                               @NonNull YoCbtAclLinkState aclLinkState,
                               @NonNull List<UUID> discoveredUuids,
                               @NonNull Map<YoCbtProfileType, YoCbtDeviceProfileState> profileStates,
                               YoCbtA2dpDeviceState a2dpState,
                               YoCbtHeadsetDeviceState headsetState,
                               @NonNull List<YoCbtSppConnectionSnapshot> sppConnections,
                               int rssi,
                               long lastSeenMillis,
                               long lastResolvedMillis,
                               long lastUpdatedMillis,
                               @NonNull YoCbtCapabilitySource source) {
        this(identity, alias, deviceClass, capability, bondState, aclLinkState, discoveredUuids,
                profileStates, a2dpState, headsetState, sppConnections, rssi, lastSeenMillis,
                lastResolvedMillis, lastUpdatedMillis, source, null,
                Collections.<YoCbtSppEndpoint>emptyList());
    }

    public YoCbtDeviceSnapshot(@NonNull YoCbtDeviceIdentity identity,
                               @NonNull YoCbtDeviceAlias alias,
                               @NonNull YoCbtDeviceClass deviceClass,
                               @NonNull YoCbtDeviceCapability capability,
                               @NonNull YoCbtBondState bondState,
                               @NonNull YoCbtAclLinkState aclLinkState,
                               @NonNull List<UUID> discoveredUuids,
                               @NonNull Map<YoCbtProfileType, YoCbtDeviceProfileState> profileStates,
                               YoCbtA2dpDeviceState a2dpState,
                               YoCbtHeadsetDeviceState headsetState,
                               @NonNull List<YoCbtSppConnectionSnapshot> sppConnections,
                               int rssi,
                               long lastSeenMillis,
                               long lastResolvedMillis,
                               long lastUpdatedMillis,
                               @NonNull YoCbtCapabilitySource source,
                               @Nullable YoCbtSdpRecord sdpRecord,
                               @NonNull List<YoCbtSppEndpoint> sdpSppEndpoints) {
        this.identity = identity;
        this.alias = alias;
        this.deviceClass = deviceClass;
        this.capability = capability;
        this.bondState = bondState;
        this.aclLinkState = aclLinkState;
        this.discoveredUuids = Collections.unmodifiableList(new ArrayList<>(discoveredUuids));
        this.profileStates = Collections.unmodifiableMap(new EnumMap<>(profileStates));
        this.a2dpState = a2dpState;
        this.headsetState = headsetState;
        this.sppConnections = Collections.unmodifiableList(new ArrayList<>(sppConnections));
        this.rssi = normalizeRssi(rssi);
        this.lastSeenMillis = lastSeenMillis;
        this.lastResolvedMillis = lastResolvedMillis;
        this.lastUpdatedMillis = lastUpdatedMillis;
        this.source = source;
        this.sdpRecord = sdpRecord;
        this.sdpSppEndpoints = Collections.unmodifiableList(new ArrayList<>(sdpSppEndpoints));
    }

    @NonNull
    public YoCbtDeviceIdentity getIdentity() {
        return identity;
    }

    @NonNull
    public YoCbtDeviceId getDeviceId() {
        return identity.getDeviceId();
    }

    @NonNull
    public YoCbtDeviceAlias getAlias() {
        return alias;
    }

    @NonNull
    public YoCbtDeviceClass getDeviceClass() {
        return deviceClass;
    }

    @NonNull
    public YoCbtDeviceCapability getCapability() {
        return capability;
    }

    @NonNull
    public YoCbtBondState getBondState() {
        return bondState;
    }

    @NonNull
    public YoCbtAclLinkState getAclLinkState() {
        return aclLinkState;
    }

    @NonNull
    public List<UUID> getDiscoveredUuids() {
        return discoveredUuids;
    }

    @NonNull
    public Map<YoCbtProfileType, YoCbtDeviceProfileState> getProfileStates() {
        return profileStates;
    }

    public YoCbtA2dpDeviceState getA2dpState() {
        return a2dpState;
    }

    public YoCbtHeadsetDeviceState getHeadsetState() {
        return headsetState;
    }

    @NonNull
    public List<YoCbtSppConnectionSnapshot> getSppConnections() {
        return sppConnections;
    }

    public int getRssi() {
        return rssi;
    }

    public boolean hasValidRssi() {
        return isValidRssi(rssi);
    }

    public static boolean isValidRssi(int rssi) {
        return rssi >= MIN_CLASSIC_RSSI && rssi <= MAX_CLASSIC_RSSI;
    }

    public static int normalizeRssi(int rssi) {
        return isValidRssi(rssi) ? rssi : RSSI_UNAVAILABLE;
    }

    public long getLastSeenMillis() {
        return lastSeenMillis;
    }

    public long getLastResolvedMillis() {
        return lastResolvedMillis;
    }

    public long getLastUpdatedMillis() {
        return lastUpdatedMillis;
    }

    @NonNull
    public YoCbtCapabilitySource getSource() {
        return source;
    }

    @Nullable
    public YoCbtSdpRecord getSdpRecord() {
        return sdpRecord;
    }

    @NonNull
    public List<YoCbtSppEndpoint> getSdpSppEndpoints() {
        return sdpSppEndpoints;
    }
}
