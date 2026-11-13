package com.ggec.earbuds.domain.device.usecase;

import com.ggec.earbuds.domain.device.model.ConnectionState;
import com.ggec.earbuds.domain.device.model.DeviceFailure;
import com.ggec.earbuds.domain.device.model.DeviceFacts;import com.ggec.earbuds.domain.device.model.DeviceRecord;
import com.ggec.earbuds.domain.device.model.DeviceRoster;
import com.ggec.earbuds.domain.device.model.DeviceSnapshot;
import com.ggec.earbuds.domain.device.model.DeviceState;
import com.ggec.earbuds.domain.device.model.DeviceStatus;
import com.ggec.earbuds.domain.device.model.DeviceTarget;
import com.ggec.earbuds.domain.device.model.RecordOrigin;
import com.ggec.earbuds.domain.device.port.DeviceRosterRepository;
import com.ggec.earbuds.domain.device.port.Subscription;
import com.ggec.earbuds.domain.device.session.DeviceSession;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * 台账投影：把「落盘台账 + 活动会话的实时事实」合成页面看到的一份设备列表。
 *
 * <p>只读，不写盘。实时覆盖只在连接中/就绪时发生（断开后由 {@link SyncActiveDevice} 落成
 * ARCHIVED 档案），因此不会用半截快照把档案抹掉。</p>
 */
public final class ObserveDeviceRoster {

    private final DeviceRosterRepository roster;
    private final DeviceSession session;
    private final LongSupplier clock;
    private final CopyOnWriteArrayList<Consumer<DeviceRoster>> listeners =
            new CopyOnWriteArrayList<>();

    private Subscription rosterSubscription;
    private Subscription sessionSubscription;
    private DeviceRoster last;

    public ObserveDeviceRoster(DeviceRosterRepository roster, DeviceSession session,
                               LongSupplier clock) {
        this.roster = Objects.requireNonNull(roster, "roster");
        this.session = Objects.requireNonNull(session, "session");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public DeviceRoster current() {
        return merge(roster.current());
    }

    public Subscription execute(Consumer<DeviceRoster> listener) {
        Objects.requireNonNull(listener, "listener");
        ensureSubscribed();
        listeners.add(listener);
        listener.accept(current());
        return () -> listeners.remove(listener);
    }

    /** 台账落盘失败（读写异常）也属于“观察台账”的一部分：页面不需要认识仓库。 */
    public Subscription observeFailures(Consumer<DeviceFailure> listener) {
        return roster.observeFailures(listener);
    }

    /** 首次读取就失败时的启动失败；页面据此进入“读不出来”态。 */
    public Optional<DeviceFailure> startupFailure() {
        return roster.startupFailure();
    }

    private void ensureSubscribed() {
        synchronized (this) {
            if (rosterSubscription != null) {
                return;
            }
            rosterSubscription = roster.observe(ignored -> publish());
            sessionSubscription = session.observe(ignored -> publish());
        }
    }

    private synchronized void publish() {
        DeviceRoster next = current();
        if (next.equals(last)) {
            return;
        }
        last = next;
        for (Consumer<DeviceRoster> listener : listeners) {
            listener.accept(next);
        }
    }

    private DeviceRoster merge(DeviceRoster stored) {
        DeviceTarget target = session.target().orElse(null);
        if (target == null) {
            return stored;
        }
        DeviceSnapshot snapshot = session.current();
        ConnectionState connection = snapshot.connection();
        if (connection != ConnectionState.CONNECTING && connection != ConnectionState.READY) {
            return stored;
        }
        Optional<DeviceRecord> existing = stored.find(target.id());
        boolean ready = connection == ConnectionState.READY;
        DeviceFacts facts = ready
                ? new DeviceFacts(snapshot.battery(), snapshot.firmware(),
                        snapshot.wearState(), snapshot.earColor())
                : existing.map(DeviceRecord::facts).orElse(DeviceFacts.unknown());
        DeviceState state = ready
                ? snapshot.state()
                : existing.map(DeviceRecord::state).orElse(new DeviceState(Map.of()));
        DeviceRecord live = new DeviceRecord(target.id(),
                existing.map(DeviceRecord::alias).orElse(""), target.identity(),
                new DeviceStatus(RecordOrigin.LIVE, connection, clock.getAsLong()), facts, state);
        return stored.upsert(live).withActive(Optional.of(target.id()));
    }
}
