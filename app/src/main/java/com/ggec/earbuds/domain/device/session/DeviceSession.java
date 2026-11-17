package com.ggec.earbuds.domain.device.session;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.device.model.BatterySnapshot;
import com.ggec.earbuds.domain.device.model.ConnectSettlement;
import com.ggec.earbuds.domain.device.model.ConnectionState;
import com.ggec.earbuds.domain.device.model.DeviceFailure;
import com.ggec.earbuds.domain.device.model.DeviceId;
import com.ggec.earbuds.domain.device.model.DeviceSnapshot;
import com.ggec.earbuds.domain.device.model.DeviceState;
import com.ggec.earbuds.domain.device.model.DeviceTarget;
import com.ggec.earbuds.domain.device.model.WearState;
import com.ggec.earbuds.domain.device.port.DeviceLinkPort;
import com.ggec.earbuds.domain.device.port.Subscription;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 活动设备会话：一次只有一台设备在线，状态由 {@link SessionState} 单点表达。
 *
 * <p><b>代数：</b>每次打开或关闭都换代（generation），上一代的连接终态回调一律丢弃；
 * 「谁被取代」由这里判定，适配层不再做仲裁。</p>
 *
 * <p><b>状态迁移（全部在锁内完成，回调在锁外调用）：</b></p>
 * <ul>
 *   <li>{@code open(target)}：换代 → 旧 pending 以 {@link DeviceFailure#REJECTED} 结算 →
 *       {@code Connecting(target)} → {@code port.disconnect()} → 更换目标时先替旧设备发一次
 *       {@code DISCONNECTED}（台账据此归档旧设备）→ 发布 {@code CONNECTING} 快照 →
 *       {@code port.connect}。同一目标已 {@code Ready} 时直接结算 {@code Ready}，不重连。</li>
 *   <li>快照 {@code READY}：{@code Connecting} → {@code Ready}。</li>
 *   <li>连接终态失败：{@code Connecting} → {@code Failed(target, failure)}。</li>
 *   <li>设备侧 {@code DISCONNECTED} 快照：{@code Ready} → {@code Idle}，清目标。</li>
 *   <li>{@code close()}：换代 → 旧 pending 以 {@code REJECTED} 结算 → {@code port.disconnect()}
 *       → 清目标 → {@code Idle} → 发布<b>唯一一次</b> {@code DISCONNECTED} 快照。</li>
 * </ul>
 *
 * <p><b>唯一生产者：</b>业务 {@code CONNECTING/DISCONNECTED} 快照由本类构造并发布；
 * 端口只在设备真实状态变化时发布快照，主动 {@code disconnect()} 不发布业务快照。</p>
 *
 * <p>线程：端口回调可能来自蓝牙线程，{@link #observe(Consumer)} 的观察者也在该线程被通知；
 * 观察者要自己切线程（页面走 LiveData 的 postValue）。</p>
 */
public final class DeviceSession {

    private final DeviceLinkPort port;
    private final Object lock = new Object();
    private final CopyOnWriteArrayList<Consumer<DeviceSnapshot>> listeners =
            new CopyOnWriteArrayList<>();

    private long generation;
    private SessionState state = new SessionState.Idle();
    private Consumer<ConnectSettlement> pending;
    private volatile DeviceSnapshot current = DeviceSnapshot.idle();

    public DeviceSession(@NonNull DeviceLinkPort port) {
        this.port = Objects.requireNonNull(port, "port");
        port.observe(this::onSnapshot);
    }

    @NonNull
    public DeviceSnapshot current() {
        return current;
    }

    /** 当前状态；只读快照，迁移只发生在 {@link #open} / {@link #close} 与端口回调里。 */
    @NonNull
    public SessionState state() {
        synchronized (lock) {
            return state;
        }
    }

    /**
     * 会话目标：{@link SessionState.Idle} 为空，其余状态返回该状态自带的设备。
     * 失败尝试的目标也在此返回——台账要据此把这次尝试的记录归档。
     */
    @NonNull
    public Optional<DeviceTarget> target() {
        synchronized (lock) {
            return targetOf(state);
        }
    }

    @NonNull
    public Subscription observe(@NonNull Consumer<DeviceSnapshot> listener) {
        Objects.requireNonNull(listener, "listener");
        listeners.add(listener);
        listener.accept(current);
        return () -> listeners.remove(listener);
    }

    /**
     * 打开一台设备。已是同一台就绪设备时直接结算就绪，不重新断连。
     * 被取代的旧请求以 {@link DeviceFailure#REJECTED} 结算，不会被静默丢弃。
     */
    public void open(@NonNull DeviceTarget next, @NonNull Consumer<ConnectSettlement> onSettled) {
        Objects.requireNonNull(next, "next");
        Objects.requireNonNull(onSettled, "onSettled");
        final boolean alreadyReady;
        final long generationNow;
        final Consumer<ConnectSettlement> superseded;
        final DeviceTarget previous;
        synchronized (lock) {
            alreadyReady = state instanceof SessionState.Ready ready && ready.target().is(next.id());
            if (alreadyReady) {
                generationNow = generation;
                superseded = null;
                previous = null;
            } else {
                generationNow = ++generation;
                superseded = pending;
                pending = onSettled;
                previous = targetOf(state).orElse(null);
                state = new SessionState.Connecting(next);
            }
        }
        if (alreadyReady) {
            // 同一台设备已经就绪：不重新断连，直接把这次意图结算成就绪。
            onSettled.accept(new ConnectSettlement.Ready());
            return;
        }
        if (superseded != null) {
            superseded.accept(new ConnectSettlement.Failed(DeviceFailure.REJECTED));
        }
        port.disconnect();
        // 换设备时先替旧设备发一次 DISCONNECTED：台账据此把旧设备归档，而不是让它停在 LIVE。
        if (previous != null && !previous.is(next.id())) {
            publish(disconnected(previous));
        }
        publish(connecting(next));
        port.connect(next, settlement -> settle(generationNow, onSettled, settlement));
    }

    /** 关闭当前会话；进行中的连接尝试以 {@link DeviceFailure#REJECTED} 结算。 */
    public void close() {
        final Consumer<ConnectSettlement> cancelled;
        final DeviceTarget previous;
        synchronized (lock) {
            generation++;
            cancelled = pending;
            pending = null;
            previous = targetOf(state).orElse(null);
            state = new SessionState.Idle();
        }
        port.disconnect();
        if (cancelled != null) {
            cancelled.accept(new ConnectSettlement.Failed(DeviceFailure.REJECTED));
        }
        // 关闭是唯一一次 DISCONNECTED：端口主动断开不再发业务快照，避免两个生产者。
        publish(disconnected(previous));
    }

    private void settle(long generationNow, Consumer<ConnectSettlement> callback,
                        ConnectSettlement settlement) {
        synchronized (lock) {
            if (generationNow != generation) {
                return;
            }
            if (pending == callback) {
                pending = null;
            }
            if (settlement instanceof ConnectSettlement.Failed failed
                    && state instanceof SessionState.Connecting connecting) {
                state = new SessionState.Failed(connecting.target(), failed.failure());
            }
        }
        callback.accept(settlement);
    }

    private void onSnapshot(@NonNull DeviceSnapshot snapshot) {
        synchronized (lock) {
            if (snapshot.connection() == ConnectionState.READY
                    && state instanceof SessionState.Connecting connecting) {
                state = new SessionState.Ready(connecting.target());
            } else if (snapshot.connection() == ConnectionState.DISCONNECTED
                    && state instanceof SessionState.Ready) {
                // 设备主动断开：会话结束，目标清空。
                state = new SessionState.Idle();
            }
        }
        publish(snapshot);
    }

    /** 一律先写 current 再通知：观察者读 {@link #current()} 与回调载荷一致。 */
    private void publish(@NonNull DeviceSnapshot snapshot) {
        current = snapshot;
        for (Consumer<DeviceSnapshot> listener : listeners) {
            listener.accept(snapshot);
        }
    }

    @NonNull
    private static Optional<DeviceTarget> targetOf(@NonNull SessionState state) {
        return switch (state) {
            case SessionState.Idle ignored -> Optional.empty();
            case SessionState.Connecting connecting -> Optional.of(connecting.target());
            case SessionState.Ready ready -> Optional.of(ready.target());
            case SessionState.Failed failed -> Optional.of(failed.target());
        };
    }

    /** 尚未有设备事实的连接中快照：身份来自目标，实时值一律未知。 */
    @NonNull
    private static DeviceSnapshot connecting(@NonNull DeviceTarget target) {
        return identityOf(target, ConnectionState.CONNECTING);
    }

    /** 该目标断开时的快照：身份仍属于该设备，实时值一律未知。 */
    @NonNull
    private static DeviceSnapshot disconnected(@Nullable DeviceTarget target) {
        return target == null
                ? DeviceSnapshot.idle()
                : identityOf(target, ConnectionState.DISCONNECTED);
    }

    @NonNull
    private static DeviceSnapshot identityOf(@NonNull DeviceTarget target,
                                             @NonNull ConnectionState connection) {
        DeviceId id = target.id();
        return new DeviceSnapshot(connection, id.modelId(),
                BatterySnapshot.unknown(), WearState.UNKNOWN, "",
                id.address(), target.identity().bleName(),
                target.identity().classicAddress(), target.identity().classicName(),
                DeviceSnapshot.UNKNOWN_EAR_COLOR, new DeviceState(Map.of()),
                Optional.empty(), List.of());
    }
}
