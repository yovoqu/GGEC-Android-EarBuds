package com.yovo.lib_bluetooth.internal.classic.discovery;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceIdentity;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceIdentityResolver;
import com.yovo.lib_bluetooth.internal.classic.device.snapshot.YoCbtDeviceSnapshot;
import com.yovo.lib_bluetooth.internal.classic.device.snapshot.YoCbtDeviceSnapshotStore;
import com.yovo.lib_bluetooth.internal.classic.device.snapshot.YoCbtObservedDevice;

import java.util.List;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 将原始发现观测合并写入中央设备快照存储。
 */
public final class YoCbtDiscoveryResultMerger {

    private final YoCbtDeviceSnapshotStore snapshotStore;
    private final YoCbtDeviceIdentityResolver identityResolver;

    public YoCbtDiscoveryResultMerger(@NonNull YoCbtDeviceSnapshotStore snapshotStore,
                                      @NonNull YoCbtDeviceIdentityResolver identityResolver) {
        this.snapshotStore = snapshotStore;
        this.identityResolver = identityResolver;
    }

    /**
     * 将单个观测设备合并入快照存储；若设备曾出现则升级身份字段（名称、类型等）。
     *
     * @param observed 发现过程中观测到的设备
     * @return 合并后的设备快照
     */
    @NonNull
    public YoCbtDeviceSnapshot merge(@NonNull YoCbtObservedDevice observed) {
        long now = observed.getObservedAtMillis();

        // 解析身份：与已有快照合并，优先保留更完整的数据
        YoCbtDeviceSnapshot existing = snapshotStore.get(observed.getDeviceId());
        YoCbtDeviceIdentity mergedIdentity;
        if (existing != null) {
            mergedIdentity = identityResolver.mergeIdentity(
                    existing.getIdentity(), observed, now);
        } else {
            mergedIdentity = observed.getIdentity();
        }

        // 更新观测数据（RSSI、最后可见时间）
        snapshotStore.updateObserved(mergedIdentity, observed.getRssi(), now);

        // 若有已知 Class of Device 数据则更新设备类别
        if (observed.getDeviceClass().isKnown()) {
            snapshotStore.updateDeviceClass(mergedIdentity, observed.getDeviceClass(), now);
        }

        // 返回更新后的完整快照
        YoCbtDeviceSnapshot result = snapshotStore.get(observed.getDeviceId());
        return result != null ? result : snapshotStore.updateObserved(
                mergedIdentity, observed.getRssi(), now);
    }

    /**
     * 返回存储中当前跟踪的所有设备的不可变快照。
     *
     * @return 设备快照列表；尚未合并任何设备时为空
     */
    @NonNull
    public List<YoCbtDeviceSnapshot> snapshot() {
        return snapshotStore.snapshot();
    }
}
