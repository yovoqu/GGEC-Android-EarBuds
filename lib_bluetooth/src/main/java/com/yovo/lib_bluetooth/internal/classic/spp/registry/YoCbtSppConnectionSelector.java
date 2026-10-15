package com.yovo.lib_bluetooth.internal.classic.spp.registry;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.spp.connection.YoCbtSppConnectionId;
import com.yovo.lib_bluetooth.internal.classic.spp.connection.YoCbtSppConnectionSnapshot;
import com.yovo.lib_bluetooth.internal.classic.spp.endpoint.YoCbtSppEndpointType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 从活动注册表按设备、端点类型等条件选择 SPP 连接。
 */
public final class YoCbtSppConnectionSelector {

    private final Map<YoCbtSppConnectionId, YoCbtSppConnectionSnapshot> snapshots;

    public YoCbtSppConnectionSelector(
            @NonNull Map<YoCbtSppConnectionId, YoCbtSppConnectionSnapshot> snapshots) {
        this.snapshots = new LinkedHashMap<>(snapshots);
    }

    @NonNull
    public List<YoCbtSppConnectionSnapshot> byDevice(@NonNull YoCbtDeviceId deviceId) {
        List<YoCbtSppConnectionSnapshot> result = new ArrayList<>();
        for (YoCbtSppConnectionSnapshot s : snapshots.values()) {
            if (deviceId.equals(s.getDeviceId())) result.add(s);
        }
        return result;
    }

    @NonNull
    public List<YoCbtSppConnectionSnapshot> byEndpointType(@NonNull YoCbtSppEndpointType type) {
        List<YoCbtSppConnectionSnapshot> result = new ArrayList<>();
        for (YoCbtSppConnectionSnapshot s : snapshots.values()) {
            if (s.getEndpoint() != null && s.getEndpoint().getType() == type) result.add(s);
        }
        return result;
    }

    @Nullable
    public YoCbtSppConnectionSnapshot byConnectionId(@NonNull YoCbtSppConnectionId id) {
        return snapshots.get(id);
    }

    public int count() { return snapshots.size(); }
    public boolean isEmpty() { return snapshots.isEmpty(); }

    @NonNull
    public List<YoCbtSppConnectionSnapshot> all() {
        return new ArrayList<>(snapshots.values());
    }
}
