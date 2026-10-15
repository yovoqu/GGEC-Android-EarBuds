package com.yovo.lib_bluetooth.internal.classic.spp.registry;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.spp.connection.YoCbtSppConnectionId;
import com.yovo.lib_bluetooth.internal.classic.spp.connection.YoCbtSppConnectionKey;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 活动 SPP 连接的注册中心，协调 Store 与 Selector。
 */

public final class YoCbtSppConnectionRegistry<T> {
    private final Map<YoCbtSppConnectionId, Entry<T>> byId = new LinkedHashMap<>();

    public synchronized void put(@NonNull YoCbtSppConnectionId id,
                                 @NonNull YoCbtSppConnectionKey key,
                                 @NonNull T connection) {
        byId.put(id, new Entry<>(key, connection));
    }

    @Nullable
    public synchronized T get(@NonNull YoCbtSppConnectionId id) {
        Entry<T> entry = byId.get(id);
        return entry == null ? null : entry.connection;
    }

    @Nullable
    public synchronized T remove(@NonNull YoCbtSppConnectionId id) {
        Entry<T> entry = byId.remove(id);
        return entry == null ? null : entry.connection;
    }

    @NonNull
    public synchronized List<T> all() {
        List<T> result = new ArrayList<>();
        for (Entry<T> entry : byId.values()) result.add(entry.connection);
        return result;
    }

    @NonNull
    public synchronized List<T> byDevice(@NonNull YoCbtDeviceId deviceId) {
        List<T> result = new ArrayList<>();
        for (Entry<T> entry : byId.values()) {
            if (entry.key.getDeviceId().equals(deviceId)) result.add(entry.connection);
        }
        return result;
    }

    @NonNull
    public synchronized List<YoCbtSppConnectionId> idsByDevice(@NonNull YoCbtDeviceId deviceId) {
        List<YoCbtSppConnectionId> result = new ArrayList<>();
        for (Map.Entry<YoCbtSppConnectionId, Entry<T>> entry : byId.entrySet()) {
            if (entry.getValue().key.getDeviceId().equals(deviceId)) result.add(entry.getKey());
        }
        return result;
    }

    @NonNull
    public synchronized List<YoCbtSppConnectionId> ids() {
        return new ArrayList<>(byId.keySet());
    }

    public synchronized void clear() {
        byId.clear();
    }

    public synchronized boolean hasActiveKey(@NonNull YoCbtSppConnectionKey key) {
        for (Entry<T> entry : byId.values()) {
            if (entry.key.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static final class Entry<T> {
        final YoCbtSppConnectionKey key;
        final T connection;

        Entry(YoCbtSppConnectionKey key, T connection) {
            this.key = key;
            this.connection = connection;
        }
    }
}
