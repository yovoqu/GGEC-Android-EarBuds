package com.yovo.lib_bluetooth.internal.ble.device;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.model.ble.BleAddressType;
import com.yovo.lib_bluetooth.model.ble.BleBondState;
import com.yovo.lib_bluetooth.model.ble.BleConnectionSnapshot;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleDeviceIdentity;
import com.yovo.lib_bluetooth.model.ble.BleDeviceSnapshot;
import com.yovo.lib_bluetooth.model.ble.BleDeviceType;
import com.yovo.lib_bluetooth.model.ble.BleGattDatabaseSnapshot;
import com.yovo.lib_bluetooth.model.ble.BleScanResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 线程安全的 BLE 设备聚合快照存储：合并扫描广告、配对状态、GATT 连接与服务发现树，返回不可变防御拷贝。
 */
public final class YoBleDeviceSnapshotStore {

    private static final class Entry {
        BleDeviceIdentity identity;
        BleBondState bondState = BleBondState.UNKNOWN;
        @Nullable
        BleConnectionSnapshot connectionSnapshot;
        @Nullable
        BleScanResult lastScanResult;
        @Nullable
        BleGattDatabaseSnapshot gattDatabase;
        long updatedAtMillis;

        Entry(@NonNull BleDeviceIdentity identity, long updatedAtMillis) {
            this.identity = identity;
            this.updatedAtMillis = updatedAtMillis;
        }

        @NonNull
        BleDeviceSnapshot toSnapshot() {
            return new BleDeviceSnapshot(
                    identity, bondState, connectionSnapshot, lastScanResult, gattDatabase, updatedAtMillis);
        }
    }

    private final Map<BleDeviceId, Entry> entries = new LinkedHashMap<>();

    public synchronized void recordScanResult(@NonNull BleScanResult scanResult) {
        BleDeviceId id = scanResult.identity().deviceId();
        Entry entry = getOrCreate(id, scanResult.identity(), scanResult.observedAtMillis());
        entry.identity = mergeIdentity(entry.identity, scanResult.identity());
        entry.lastScanResult = scanResult;
        entry.updatedAtMillis = scanResult.observedAtMillis();
    }

    public synchronized void recordIdentity(@NonNull BleDeviceIdentity identity,
                                            @Nullable BleBondState bondState,
                                            long nowMillis) {
        Entry entry = getOrCreate(identity.deviceId(), identity, nowMillis);
        entry.identity = mergeIdentity(entry.identity, identity);
        if (bondState != null) {
            entry.bondState = bondState;
        }
        entry.updatedAtMillis = nowMillis;
    }

    public synchronized void recordConnectionSnapshot(@NonNull BleConnectionSnapshot snapshot) {
        BleDeviceId id = snapshot.deviceId();
        Entry entry = getOrCreate(id, defaultIdentity(id, snapshot.updatedAtMillis()),
                snapshot.updatedAtMillis());
        entry.connectionSnapshot = snapshot;
        if (snapshot.bondState() != BleBondState.UNKNOWN) {
            entry.bondState = snapshot.bondState();
        }
        entry.updatedAtMillis = snapshot.updatedAtMillis();
    }

    public synchronized void recordGattDatabase(@NonNull BleGattDatabaseSnapshot database) {
        BleDeviceId id = database.deviceId();
        Entry entry = getOrCreate(id, defaultIdentity(id, database.discoveredAtMillis()),
                database.discoveredAtMillis());
        entry.gattDatabase = database;
        entry.updatedAtMillis = database.discoveredAtMillis();
    }

    public synchronized void clearGattDatabase(@NonNull BleDeviceId deviceId, long nowMillis) {
        Entry entry = entries.get(deviceId);
        if (entry != null) {
            entry.gattDatabase = null;
            entry.updatedAtMillis = nowMillis;
        }
    }

    public synchronized void recordBondState(@NonNull BleDeviceId deviceId,
                                             @NonNull BleBondState bondState,
                                             long nowMillis) {
        Entry entry = getOrCreate(deviceId, defaultIdentity(deviceId, nowMillis), nowMillis);
        entry.bondState = bondState;
        entry.updatedAtMillis = nowMillis;
    }

    @Nullable
    public synchronized BleDeviceSnapshot get(@NonNull BleDeviceId deviceId) {
        Entry entry = entries.get(deviceId);
        return entry == null ? null : entry.toSnapshot();
    }

    @NonNull
    public synchronized List<BleDeviceSnapshot> all() {
        List<BleDeviceSnapshot> list = new ArrayList<>(entries.size());
        for (Entry entry : entries.values()) {
            list.add(entry.toSnapshot());
        }
        return Collections.unmodifiableList(list);
    }

    public synchronized void clear() {
        entries.clear();
    }

    private Entry getOrCreate(@NonNull BleDeviceId deviceId,
                              @NonNull BleDeviceIdentity identity,
                              long nowMillis) {
        Entry entry = entries.get(deviceId);
        if (entry == null) {
            entry = new Entry(identity, nowMillis);
            entries.put(deviceId, entry);
        }
        return entry;
    }

    private static BleDeviceIdentity mergeIdentity(@NonNull BleDeviceIdentity existing,
                                                   @NonNull BleDeviceIdentity incoming) {
        String name = !incoming.displayName().isEmpty()
                ? incoming.displayName() : existing.displayName();
        String alias = incoming.alias() != null ? incoming.alias() : existing.alias();
        BleAddressType addrType = incoming.addressType() != BleAddressType.UNKNOWN
                ? incoming.addressType() : existing.addressType();
        BleDeviceType devType = incoming.deviceType() != BleDeviceType.UNKNOWN
                ? incoming.deviceType() : existing.deviceType();
        return new BleDeviceIdentity(
                existing.deviceId(), name, alias, addrType, devType,
                Math.max(existing.updatedAtMillis(), incoming.updatedAtMillis()));
    }

    private static BleDeviceIdentity defaultIdentity(@NonNull BleDeviceId deviceId, long nowMillis) {
        return new BleDeviceIdentity(deviceId, "", null,
                BleAddressType.UNKNOWN, BleDeviceType.LE, nowMillis);
    }
}
