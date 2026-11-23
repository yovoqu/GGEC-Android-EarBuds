package com.ggec.earbuds.domain.device.usecase;

import com.ggec.earbuds.domain.device.model.ConnectionState;
import com.ggec.earbuds.domain.device.model.DeviceRecord;
import com.ggec.earbuds.domain.device.model.DeviceSnapshot;
import com.ggec.earbuds.domain.device.model.DeviceTarget;
import com.ggec.earbuds.domain.device.port.DeviceRosterRepository;
import com.ggec.earbuds.domain.device.port.Subscription;
import com.ggec.earbuds.domain.device.session.DeviceSession;

import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * 把活动会话的事实写回台账：活动设备标 LIVE，掉线转 ARCHIVED 并**清空实时值**，只留
 * 名称/地址/型号与最后同步时间。
 *
 * <p>台账的唯一写者就是本类（别名/遗忘等用户操作除外）。连接中与断开时保留上一份设备事实与
 * 能力值——断开瞬间适配层会清空会话事实，若照抄就会把档案抹掉。合成规则单点在
 * {@link LiveRecord}。</p>
 *
 * <p><b>生命周期：</b>本类不在构造期自行订阅。装配根显式调用 {@link #start()} 开始跟踪、
 * {@link #stop()} 释放订阅，两者都幂等。</p>
 */
public final class SyncActiveDevice {

    private final DeviceRosterRepository roster;
    private final DeviceSession session;
    private final LongSupplier clock;

    private Subscription subscription;

    public SyncActiveDevice(DeviceRosterRepository roster, DeviceSession session,
                            LongSupplier clock) {
        this.roster = Objects.requireNonNull(roster, "roster");
        this.session = Objects.requireNonNull(session, "session");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** 开始跟踪活动会话；重复调用为幂等 no-op。 */
    public synchronized void start() {
        if (subscription != null) {
            return;
        }
        subscription = session.observe(this::onSnapshot);
    }

    /** 停止跟踪并释放订阅；重复调用为幂等 no-op。 */
    public synchronized void stop() {
        if (subscription == null) {
            return;
        }
        subscription.unsubscribe();
        subscription = null;
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
        roster.update(current -> LiveRecord.apply(current, target, snapshot, now));
    }

    /**
     * 断开快照可能属于“刚被换下来的旧设备”，此时不能拿会话当前目标顶替：
     * 先按快照里的 modelId + 地址找台账记录，找不到才回落到会话目标。
     */
    private DeviceTarget resolveTarget(DeviceSnapshot snapshot) {
        if (snapshot.connection() == ConnectionState.DISCONNECTED) {
            for (DeviceRecord record : roster.current().devices()) {
                if (record.id().modelId().equals(snapshot.modelId())
                        && record.id().address().equals(snapshot.address())) {
                    return DeviceTarget.of(record);
                }
            }
        }
        return session.target().orElse(null);
    }
}
