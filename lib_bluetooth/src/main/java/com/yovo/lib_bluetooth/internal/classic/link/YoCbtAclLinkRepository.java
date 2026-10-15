package com.yovo.lib_bluetooth.internal.classic.link;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.link.YoCbtAclLinkState;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.device.snapshot.YoCbtDeviceSnapshot;
import com.yovo.lib_bluetooth.internal.classic.device.snapshot.YoCbtDeviceSnapshotStore;

import java.util.ArrayList;
import java.util.List;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description ACL 链路状态查询仓库，合并 {@link YoCbtAclLinkTracker} 内存状态与 {@link YoCbtDeviceSnapshotStore} 提供统一视图。
 */
public final class YoCbtAclLinkRepository {

    private final YoCbtAclLinkTracker tracker;
    private final YoCbtDeviceSnapshotStore snapshotStore;

    public YoCbtAclLinkRepository(@NonNull YoCbtAclLinkTracker tracker,
                                   @NonNull YoCbtDeviceSnapshotStore snapshotStore) {
        this.tracker = tracker;
        this.snapshotStore = snapshotStore;
    }

    /**
     * 返回指定设备的 ACL 链路状态。
     *
     * <p>以 tracker 状态为准；未跟踪时回退查询快照存储。
     *
     * @return ACL 状态，不为 null
     */
    @NonNull
    public YoCbtAclLinkState getAclState(@NonNull YoCbtDeviceId deviceId) {
        YoCbtAclLinkState state = tracker.getState(deviceId);
        if (state != YoCbtAclLinkState.UNKNOWN) return state;

        YoCbtDeviceSnapshot snapshot = snapshotStore.get(deviceId);
        if (snapshot != null) return snapshot.getAclLinkState();

        return YoCbtAclLinkState.UNKNOWN;
    }

    /**
     * 若设备当前 ACL 已连接则返回 {@code true}。
     *
     * <p>等价于 {@code getAclState(deviceId) == YoCbtAclLinkState.CONNECTED}。
     */
    public boolean isAclConnected(@NonNull YoCbtDeviceId deviceId) {
        return getAclState(deviceId) == YoCbtAclLinkState.CONNECTED;
    }

    /**
     * 返回 tracker 中状态为 {@link YoCbtAclLinkState#CONNECTED} 的所有设备 ID 列表。
     */
    @NonNull
    public List<YoCbtDeviceId> getConnectedDevices() {
        List<YoCbtDeviceId> connected = new ArrayList<>();
        for (java.util.Map.Entry<YoCbtDeviceId, YoCbtAclLinkState> entry
                : tracker.allStates().entrySet()) {
            if (entry.getValue() == YoCbtAclLinkState.CONNECTED) {
                connected.add(entry.getKey());
            }
        }
        for (YoCbtDeviceSnapshot snapshot : snapshotStore.snapshot()) {
            YoCbtDeviceId deviceId = snapshot.getDeviceId();
            if (snapshot.getAclLinkState() == YoCbtAclLinkState.CONNECTED
                    && tracker.getState(deviceId) == YoCbtAclLinkState.UNKNOWN
                    && !connected.contains(deviceId)) {
                connected.add(deviceId);
            }
        }
        return connected;
    }
}
