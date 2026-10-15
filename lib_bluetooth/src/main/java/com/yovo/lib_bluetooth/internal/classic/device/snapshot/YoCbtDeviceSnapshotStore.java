package com.yovo.lib_bluetooth.internal.classic.device.snapshot;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.classic.device.capability.YoCbtDeviceCapability;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceAlias;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceClass;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceIdentity;
import com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtDeviceProfileState;
import com.yovo.lib_bluetooth.internal.classic.profiles.a2dp.YoCbtA2dpDeviceState;
import com.yovo.lib_bluetooth.internal.classic.profiles.headset.YoCbtHeadsetDeviceState;
import com.yovo.lib_bluetooth.internal.classic.link.YoCbtAclLinkState;
import com.yovo.lib_bluetooth.internal.classic.pairing.YoCbtBondState;
import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtCapabilitySource;
import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtProfileType;
import com.yovo.lib_bluetooth.internal.classic.service.model.YoCbtSdpRecord;
import com.yovo.lib_bluetooth.internal.classic.spp.connection.YoCbtSppConnectionSnapshot;
import com.yovo.lib_bluetooth.internal.classic.spp.endpoint.YoCbtSppEndpoint;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 设备快照的线程安全内存存储，提供按事件类型增量更新与查询。
 */

public final class YoCbtDeviceSnapshotStore {
    private final Map<YoCbtDeviceId, MutableRecord> records = new LinkedHashMap<>();

    public synchronized YoCbtDeviceSnapshot updateObserved(@NonNull YoCbtDeviceIdentity identity,
                                                          int rssi,
                                                          long timestampMillis) {
        MutableRecord record = record(identity);
        record.identity = prefer(record.identity, identity);
        record.alias = record.alias.merge(identity.getAlias());
        record.rssi = rssi;
        record.lastSeenMillis = timestampMillis;
        record.lastUpdatedMillis = timestampMillis;
        return record.snapshot();
    }

    public synchronized YoCbtDeviceSnapshot updateBond(@NonNull YoCbtDeviceIdentity identity,
                                                      @NonNull YoCbtBondState state,
                                                      long timestampMillis) {
        MutableRecord record = record(identity);
        record.identity = prefer(record.identity, identity);
        record.alias = record.alias.merge(identity.getAlias());
        record.bondState = state;
        record.lastUpdatedMillis = timestampMillis;
        return record.snapshot();
    }

    public synchronized YoCbtDeviceSnapshot updateAcl(@NonNull YoCbtDeviceIdentity identity,
                                                     @NonNull YoCbtAclLinkState state,
                                                     long timestampMillis) {
        MutableRecord record = record(identity);
        record.identity = prefer(record.identity, identity);
        record.alias = record.alias.merge(identity.getAlias());
        record.aclState = state;
        record.lastUpdatedMillis = timestampMillis;
        return record.snapshot();
    }

    public synchronized YoCbtDeviceSnapshot updateUuids(@NonNull YoCbtDeviceIdentity identity,
                                                       @NonNull List<UUID> uuids,
                                                       long timestampMillis) {
        MutableRecord record = record(identity);
        record.identity = prefer(record.identity, identity);
        record.uuids.clear();
        record.uuids.addAll(uuids);
        record.lastResolvedMillis = timestampMillis;
        record.lastUpdatedMillis = timestampMillis;
        return record.snapshot();
    }

    public synchronized YoCbtDeviceSnapshot updateDeviceClass(@NonNull YoCbtDeviceIdentity identity,
                                                             @NonNull YoCbtDeviceClass deviceClass,
                                                             long timestampMillis) {
        MutableRecord record = record(identity);
        record.deviceClass = deviceClass;
        record.lastUpdatedMillis = timestampMillis;
        return record.snapshot();
    }

    public synchronized YoCbtDeviceSnapshot updateCapability(@NonNull YoCbtDeviceIdentity identity,
                                                            @NonNull YoCbtProfileType profileType,
                                                            @NonNull YoCbtCapabilitySource source,
                                                            long timestampMillis) {
        MutableRecord record = record(identity);
        record.capability = record.capability.withProfile(profileType, source);
        record.source = source;
        record.lastResolvedMillis = timestampMillis;
        record.lastUpdatedMillis = timestampMillis;
        return record.snapshot();
    }

    public synchronized YoCbtDeviceSnapshot updateProfile(@NonNull YoCbtDeviceProfileState state) {
        MutableRecord record = records.get(state.getDeviceId());
        if (record == null) return null;
        record.profileStates.put(state.getProfileType(), state);
        record.lastUpdatedMillis = state.getLastChangedAtMillis();
        return record.snapshot();
    }

    public synchronized YoCbtDeviceSnapshot updateA2dp(@NonNull YoCbtA2dpDeviceState state) {
        MutableRecord record = records.get(state.getDeviceId());
        if (record == null) return null;
        record.a2dpState = state;
        record.lastUpdatedMillis = state.getLastChangedAtMillis();
        return record.snapshot();
    }

    public synchronized YoCbtDeviceSnapshot updateHeadset(@NonNull YoCbtHeadsetDeviceState state) {
        MutableRecord record = records.get(state.getDeviceId());
        if (record == null) return null;
        record.headsetState = state;
        record.lastUpdatedMillis = state.getLastChangedAtMillis();
        return record.snapshot();
    }

    public synchronized void replaceSppSnapshots(@NonNull YoCbtDeviceId deviceId,
                                                 @NonNull List<YoCbtSppConnectionSnapshot> snapshots,
                                                 long timestampMillis) {
        MutableRecord record = records.get(deviceId);
        if (record == null) return;
        record.sppSnapshots.clear();
        record.sppSnapshots.addAll(snapshots);
        record.lastUpdatedMillis = timestampMillis;
    }

    public synchronized YoCbtDeviceSnapshot applySdpResolution(@NonNull YoCbtDeviceIdentity identity,
                                                               @NonNull YoCbtSdpRecord sdpRecord,
                                                               @NonNull List<YoCbtSppEndpoint> sppEndpoints,
                                                               @NonNull Set<YoCbtProfileType> profiles,
                                                               @NonNull YoCbtCapabilitySource source,
                                                               long timestampMillis) {
        MutableRecord record = record(identity);
        record.identity = prefer(record.identity, identity);
        record.uuids.clear();
        record.uuids.addAll(sdpRecord.getResult().getUuids());
        record.sdpRecord = sdpRecord;
        record.sdpSppEndpoints.clear();
        record.sdpSppEndpoints.addAll(sppEndpoints);
        for (YoCbtProfileType profileType : profiles) {
            record.capability = record.capability.withProfile(profileType, source);
        }
        record.source = source;
        record.lastResolvedMillis = timestampMillis;
        record.lastUpdatedMillis = timestampMillis;
        return record.snapshot();
    }

    @Nullable
    public synchronized YoCbtDeviceSnapshot get(@NonNull YoCbtDeviceId deviceId) {
        MutableRecord record = records.get(deviceId);
        return record == null ? null : record.snapshot();
    }

    @NonNull
    public synchronized List<YoCbtDeviceSnapshot> snapshot() {
        List<YoCbtDeviceSnapshot> result = new ArrayList<>();
        for (MutableRecord record : records.values()) {
            result.add(record.snapshot());
        }
        return result;
    }

    public synchronized void clear() {
        records.clear();
    }

    private MutableRecord record(YoCbtDeviceIdentity identity) {
        MutableRecord record = records.get(identity.getDeviceId());
        if (record == null) {
            record = new MutableRecord();
            record.identity = identity;
            records.put(identity.getDeviceId(), record);
        }
        return record;
    }

    private static YoCbtDeviceIdentity prefer(YoCbtDeviceIdentity current, YoCbtDeviceIdentity incoming) {
        if (current == null) return incoming;
        if (current.getDisplayName().isEmpty() && !incoming.getDisplayName().isEmpty()) return incoming;
        return current;
    }

    private static final class MutableRecord {
        YoCbtDeviceIdentity identity;
        YoCbtDeviceAlias alias = YoCbtDeviceAlias.empty();
        YoCbtDeviceClass deviceClass = YoCbtDeviceClass.unknown();
        YoCbtDeviceCapability capability = YoCbtDeviceCapability.empty();
        YoCbtBondState bondState = YoCbtBondState.UNKNOWN;
        YoCbtAclLinkState aclState = YoCbtAclLinkState.UNKNOWN;
        final List<UUID> uuids = new ArrayList<>();
        final EnumMap<YoCbtProfileType, YoCbtDeviceProfileState> profileStates =
                new EnumMap<>(YoCbtProfileType.class);
        YoCbtA2dpDeviceState a2dpState;
        YoCbtHeadsetDeviceState headsetState;
        final List<YoCbtSppConnectionSnapshot> sppSnapshots = new ArrayList<>();
        YoCbtSdpRecord sdpRecord;
        final List<YoCbtSppEndpoint> sdpSppEndpoints = new ArrayList<>();
        int rssi = YoCbtDeviceSnapshot.RSSI_UNAVAILABLE;
        long lastSeenMillis;
        long lastResolvedMillis;
        long lastUpdatedMillis;
        YoCbtCapabilitySource source = YoCbtCapabilitySource.UNKNOWN;

        YoCbtDeviceSnapshot snapshot() {
            return new YoCbtDeviceSnapshot(identity, alias, deviceClass, capability, bondState,
                    aclState, uuids, profileStates, a2dpState, headsetState, sppSnapshots, rssi, lastSeenMillis,
                    lastResolvedMillis, lastUpdatedMillis, source, sdpRecord, sdpSppEndpoints);
        }
    }
}
