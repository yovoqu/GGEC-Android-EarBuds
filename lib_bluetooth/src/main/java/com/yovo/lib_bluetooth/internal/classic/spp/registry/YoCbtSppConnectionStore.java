package com.yovo.lib_bluetooth.internal.classic.spp.registry;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.spp.connection.YoCbtSppConnectionId;
import com.yovo.lib_bluetooth.internal.classic.spp.connection.YoCbtSppConnectionSnapshot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 活动 SPP 连接快照的线程安全存储，支持按设备、ID 查询。
 */
public final class YoCbtSppConnectionStore {

    private final Map<YoCbtSppConnectionId, YoCbtSppConnectionSnapshot> snapshots =
            new LinkedHashMap<>();

    public synchronized void put(@NonNull YoCbtSppConnectionSnapshot snapshot) {
        snapshots.put(snapshot.getConnectionId(), snapshot);
    }

    @Nullable
    public synchronized YoCbtSppConnectionSnapshot get(@NonNull YoCbtSppConnectionId id) {
        return snapshots.get(id);
    }

    @Nullable
    public synchronized YoCbtSppConnectionSnapshot remove(@NonNull YoCbtSppConnectionId id) {
        return snapshots.remove(id);
    }

    @NonNull
    public synchronized List<YoCbtSppConnectionSnapshot> byDevice(@NonNull YoCbtDeviceId deviceId) {
        List<YoCbtSppConnectionSnapshot> result = new ArrayList<>();
        for (YoCbtSppConnectionSnapshot s : snapshots.values()) {
            if (deviceId.equals(s.getDeviceId())) result.add(s);
        }
        return result;
    }

    @NonNull
    public synchronized List<YoCbtSppConnectionSnapshot> all() {
        return new ArrayList<>(snapshots.values());
    }

    public synchronized int count() { return snapshots.size(); }
    public synchronized boolean isEmpty() { return snapshots.isEmpty(); }
    public synchronized void clear() { snapshots.clear(); }
}
