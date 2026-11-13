package com.ggec.earbuds.domain.device.usecase;

import com.ggec.earbuds.domain.device.model.ConnectionState;
import com.ggec.earbuds.domain.device.model.DeviceFacts;
import com.ggec.earbuds.domain.device.model.DeviceRecord;
import com.ggec.earbuds.domain.device.model.DeviceRoster;
import com.ggec.earbuds.domain.device.model.DeviceSnapshot;
import com.ggec.earbuds.domain.device.model.DeviceState;
import com.ggec.earbuds.domain.device.model.DeviceStatus;
import com.ggec.earbuds.domain.device.model.DeviceTarget;
import com.ggec.earbuds.domain.device.model.RecordOrigin;
import com.ggec.earbuds.domain.device.port.DeviceRosterRepository;
import com.ggec.earbuds.domain.device.session.DeviceSession;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * 把活动会话的事实写回台账：活动设备标 LIVE，掉线转 ARCHIVED 并**清空实时值**，只留
 * 名称/地址/型号与最后同步时间。
 *
 * <p>台账的唯一写者就是本类（别名/遗忘等用户操作除外）。连接中与断开时保留上一份设备事实与
 * 能力值——断开瞬间适配层会清空会话事实，若照抄就会把档案抹掉。</p>
 */
public final class SyncActiveDevice {

    private final DeviceRosterRepository roster;
    private final DeviceSession session;
    private final LongSupplier clock;

    public SyncActiveDevice(DeviceRosterRepository roster, DeviceSession session, LongSupplier clock) {
        this.roster = Objects.requireNonNull(roster, "roster");
        this.session = Objects.requireNonNull(session, "session");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** 装配根在进程启动时调用一次。 */
    public void start() {
        session.observe(this::onSnapshot);
    }

    private void onSnapshot(DeviceSnapshot snapshot) {
        DeviceTarget target = resolveTarget(snapshot);
        if (target == null) {
            return;
        }
        ConnectionState connection = snapshot.connection();
        if (connection == ConnectionState.IDLE) {
            return;
        }
        long now = clock.getAsLong();
        roster.update(current -> reconcile(current, target, snapshot, connection, now));
    }

    /**
     * 断开快照可能属于“刚被换下来的旧设备”，此时不能拿会话当前目标顶替：
     * 先按快照里的 modelId + BLE 地址找台账记录，找不到才回落到会话目标。
     */
    private DeviceTarget resolveTarget(DeviceSnapshot snapshot) {
        if (snapshot.connection() == ConnectionState.DISCONNECTED) {
            for (DeviceRecord record : roster.current().devices()) {
                if (record.id().modelId().equals(snapshot.modelId())
                        && record.id().bleAddress().equals(snapshot.bleAddress())) {
                    return DeviceTarget.of(record);
                }
            }
        }
        return session.target().orElse(null);
    }

    private static DeviceRoster reconcile(DeviceRoster current, DeviceTarget target,
                                          DeviceSnapshot snapshot, ConnectionState connection,
                                          long now) {
        Optional<DeviceRecord> existing = current.find(target.id());
        String alias = existing.map(DeviceRecord::alias).orElse("");
        boolean ready = connection == ConnectionState.READY;
        boolean disconnected = connection == ConnectionState.DISCONNECTED;
        // 断开后只留身份元信息与最后同步时间：电量/固件/佩戴这些实时值属于“会话内的事实”，
        // 掉线后继续展示旧数值会把历史当成现状。
        DeviceFacts facts = ready
                ? new DeviceFacts(snapshot.battery(), snapshot.firmware(),
                        snapshot.wearState(), snapshot.earColor())
                : disconnected
                        ? DeviceFacts.unknown()
                        : existing.map(DeviceRecord::facts).orElse(DeviceFacts.unknown());
        DeviceState state = ready
                ? snapshot.state()
                : disconnected
                        ? new DeviceState(Map.of())
                        : existing.map(DeviceRecord::state).orElse(new DeviceState(Map.of()));
        DeviceStatus status = disconnected
                ? DeviceStatus.archived(now)
                : new DeviceStatus(RecordOrigin.LIVE, connection, now);
        DeviceRecord record = new DeviceRecord(target.id(), alias, target.identity(),
                status, facts, state);
        DeviceRoster next = current.upsert(record);
        return disconnected
                ? next.withActive(current.activeId())
                : next.withActive(Optional.of(target.id()));
    }
}
