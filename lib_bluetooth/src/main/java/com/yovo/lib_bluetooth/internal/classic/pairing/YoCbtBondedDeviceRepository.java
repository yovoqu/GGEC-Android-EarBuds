package com.yovo.lib_bluetooth.internal.classic.pairing;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtAddressType;
import com.yovo.lib_bluetooth.internal.classic.pairing.YoCbtBondState;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceIdentity;
import com.yovo.lib_bluetooth.internal.classic.device.snapshot.YoCbtDeviceSnapshot;
import com.yovo.lib_bluetooth.internal.classic.device.snapshot.YoCbtDeviceSnapshotStore;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceType;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 已配对设备仓库，基于 {@link YoCbtDeviceSnapshotStore} 提供 Bond 设备视图。
 */
public final class YoCbtBondedDeviceRepository {

    private final YoCbtDeviceSnapshotStore snapshotStore;

    /** @param snapshotStore 共享设备快照存储 */
    public YoCbtBondedDeviceRepository(@NonNull YoCbtDeviceSnapshotStore snapshotStore) {
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore must not be null");
    }

    /**
     * 返回当前跟踪的所有设备的快照。包括已通过存储观测或创建的
     * 已配对与未配对设备。
     *
     * @return 不可修改的设备快照列表；无跟踪设备时为空
     */
    @NonNull
    public List<YoCbtDeviceSnapshot> getAll() {
        return Collections.unmodifiableList(snapshotStore.snapshot());
    }

    /**
     * 返回给定设备的快照；存储中未跟踪该设备时返回 {@code null}。
     *
     * @param deviceId 要查找的设备
     * @return 设备快照，或 {@code null}
     */
    @Nullable
    public YoCbtDeviceSnapshot get(@NonNull YoCbtDeviceId deviceId) {
        return snapshotStore.get(deviceId);
    }

    /**
     * 确保给定快照在存储中有对应记录。若设备已存在则合并快照（保留已有字段，
     * 传入的非 null 字段更新记录）；若不存在则用快照数据创建新记录。
     *
     * <p>返回值反映操作后的合并状态。
     *
     * @param snapshot 要添加或合并的设备快照
     * @return 合并后的结果快照
     */
    @NonNull
    public YoCbtDeviceSnapshot add(@NonNull YoCbtDeviceSnapshot snapshot) {
        // 使用 updateObserved 作为身份与发现数据的通用 upsert
        long now = Math.max(snapshot.getLastUpdatedMillis(), System.currentTimeMillis());
        return snapshotStore.updateObserved(snapshot.getIdentity(), snapshot.getRssi(), now);
    }

    /**
     * 从跟踪存储中移除给定设备（若存在）。
     *
     * <p>在快照存储中将设备配对状态标记为 {@link YoCbtBondState#REMOVED}。
     * 存储保留设备记录但标记为不再已配对。
     *
     * @param deviceId 要移除的设备
     */
    public void remove(@NonNull YoCbtDeviceId deviceId) {
        YoCbtDeviceIdentity identity = new YoCbtDeviceIdentity(
                deviceId,
                deviceId.value(),
                YoCbtAddressType.CLASSIC,
                YoCbtDeviceType.UNKNOWN,
                null);
        snapshotStore.updateBond(identity, YoCbtBondState.REMOVED, System.currentTimeMillis());
    }

    /**
     * 用较新数据更新存储中给定设备的快照。
     *
     * <p>委托快照存储的更新机制，将传入身份与配对状态与已有数据合并。
     *
     * @param snapshot 更新后的快照
     * @return 合并后的结果快照
     */
    @NonNull
    public YoCbtDeviceSnapshot update(@NonNull YoCbtDeviceSnapshot snapshot) {
        long now = System.currentTimeMillis();
        snapshotStore.updateObserved(snapshot.getIdentity(), snapshot.getRssi(), now);
        return snapshotStore.updateBond(snapshot.getIdentity(), snapshot.getBondState(), now);
    }

    /**
     * 从存储中移除所有跟踪设备。
     */
    public void clear() {
        snapshotStore.clear();
    }

    /**
     * 返回存储中当前跟踪的设备数量。
     */
    public int size() {
        return snapshotStore.snapshot().size();
    }
}
