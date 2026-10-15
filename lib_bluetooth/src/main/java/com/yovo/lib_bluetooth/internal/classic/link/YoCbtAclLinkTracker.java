package com.yovo.lib_bluetooth.internal.classic.link;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.link.YoCbtAclLinkState;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceIdentity;
import com.yovo.lib_bluetooth.internal.classic.device.snapshot.YoCbtDeviceSnapshot;
import com.yovo.lib_bluetooth.internal.classic.device.snapshot.YoCbtDeviceSnapshotStore;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 跟踪所有已知设备的 ACL 链路状态，与 {@link YoCbtDeviceSnapshotStore} 同步维护内存映射，线程安全。
 */
public final class YoCbtAclLinkTracker {

    private final YoCbtDeviceSnapshotStore snapshotStore;
    private final Map<YoCbtDeviceId, YoCbtAclLinkState> states = new LinkedHashMap<>();

    /**
     * @param snapshotStore 共享设备快照存储，每次 ACL 状态变更时同步更新
     */
    public YoCbtAclLinkTracker(@NonNull YoCbtDeviceSnapshotStore snapshotStore) {
        this.snapshotStore = snapshotStore;
    }

    /**
     * 更新指定设备的 ACL 状态并返回更新后的快照；若尚未跟踪则创建新条目。
     *
     * @param identity  设备身份
     * @param state     新的 ACL 链路状态
     * @param timestamp 事件时间戳（毫秒）
     * @return 更新后的设备快照
     */
    @NonNull
    public synchronized YoCbtDeviceSnapshot updateAclState(
            @NonNull YoCbtDeviceIdentity identity,
            @NonNull YoCbtAclLinkState state,
            long timestamp) {
        states.put(identity.getDeviceId(), state);

        YoCbtDeviceSnapshot snapshot = snapshotStore.updateAcl(identity, state, timestamp);

        return snapshot;
    }

    /**
     * 返回指定设备当前的 ACL 链路状态；未跟踪时返回 {@link YoCbtAclLinkState#UNKNOWN}。
     */
    @NonNull
    public synchronized YoCbtAclLinkState getState(@NonNull YoCbtDeviceId deviceId) {
        YoCbtAclLinkState state = states.get(deviceId);
        return state != null ? state : YoCbtAclLinkState.UNKNOWN;
    }

    /** 返回所有已跟踪设备与 ACL 状态映射的不可修改快照。 */
    @NonNull
    public synchronized Map<YoCbtDeviceId, YoCbtAclLinkState> allStates() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(states));
    }

    /** 清除所有已跟踪的 ACL 状态条目，不会清空底层快照存储。 */
    public synchronized void clear() {
        states.clear();
    }
}
