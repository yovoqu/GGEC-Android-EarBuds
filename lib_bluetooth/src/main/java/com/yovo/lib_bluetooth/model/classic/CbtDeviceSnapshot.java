package com.yovo.lib_bluetooth.model.classic;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 经典蓝牙设备快照。
 */
public final class CbtDeviceSnapshot {

    public static final int RSSI_UNAVAILABLE = Short.MIN_VALUE;

    @NonNull
    private final CbtDeviceIdentity identity;
    @NonNull
    private final CbtBondState bondState;
    @NonNull
    private final CbtAclLinkState aclLinkState;
    private final int rssi;
    private final long lastSeenMillis;
    private final long lastUpdatedMillis;
    @NonNull
    private final List<CbtSppConnectionSnapshot> sppConnections;
    @NonNull
    private final Map<CbtProfileType, CbtDeviceProfileState> profileStates;
    @NonNull
    private final List<UUID> discoveredUuids;
    @NonNull
    private final List<CbtSppEndpoint> sdpSppEndpoints;

    public CbtDeviceSnapshot(@NonNull CbtDeviceIdentity identity,
                             @NonNull CbtBondState bondState,
                             @NonNull CbtAclLinkState aclLinkState,
                             int rssi,
                             long lastSeenMillis,
                             long lastUpdatedMillis,
                             @NonNull List<CbtSppConnectionSnapshot> sppConnections,
                             @NonNull Map<CbtProfileType, CbtDeviceProfileState> profileStates,
                             @NonNull List<UUID> discoveredUuids,
                             @NonNull List<CbtSppEndpoint> sdpSppEndpoints) {
        this.identity = identity;
        this.bondState = bondState;
        this.aclLinkState = aclLinkState;
        this.rssi = rssi;
        this.lastSeenMillis = lastSeenMillis;
        this.lastUpdatedMillis = lastUpdatedMillis;
        this.sppConnections = Collections.unmodifiableList(new ArrayList<>(sppConnections));
        this.profileStates = Collections.unmodifiableMap(new EnumMap<>(profileStates));
        this.discoveredUuids = Collections.unmodifiableList(new ArrayList<>(discoveredUuids));
        this.sdpSppEndpoints = Collections.unmodifiableList(new ArrayList<>(sdpSppEndpoints));
    }

    @NonNull
    public CbtDeviceIdentity identity() {
        return identity;
    }

    @NonNull
    public CbtDeviceId deviceId() {
        return identity.deviceId();
    }

    @NonNull
    public CbtBondState bondState() {
        return bondState;
    }

    @NonNull
    public CbtAclLinkState aclLinkState() {
        return aclLinkState;
    }

    public int rssi() {
        return rssi;
    }

    public boolean hasValidRssi() {
        return isValidRssi(rssi);
    }

    public static boolean isValidRssi(int rssi) {
        return rssi >= -127 && rssi <= 20;
    }

    public static int normalizeRssi(int rssi) {
        return isValidRssi(rssi) ? rssi : RSSI_UNAVAILABLE;
    }

    public long lastSeenMillis() {
        return lastSeenMillis;
    }

    public long lastUpdatedMillis() {
        return lastUpdatedMillis;
    }

    @NonNull
    public List<CbtSppConnectionSnapshot> sppConnections() {
        return sppConnections;
    }

    @NonNull
    public Map<CbtProfileType, CbtDeviceProfileState> profileStates() {
        return profileStates;
    }

    @NonNull
    public List<UUID> discoveredUuids() {
        return discoveredUuids;
    }

    @NonNull
    public List<CbtSppEndpoint> sdpSppEndpoints() {
        return sdpSppEndpoints;
    }
}
