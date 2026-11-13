package com.ggec.earbuds.domain.device.session;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.device.model.BatterySnapshot;
import com.ggec.earbuds.domain.device.model.ConnectSettlement;
import com.ggec.earbuds.domain.device.model.ConnectionState;
import com.ggec.earbuds.domain.device.model.DeviceState;
import com.ggec.earbuds.domain.device.model.WearState;
import com.ggec.earbuds.domain.device.model.DeviceFailure;
import com.ggec.earbuds.domain.device.model.DeviceSnapshot;
import com.ggec.earbuds.domain.device.model.DeviceTarget;
import com.ggec.earbuds.domain.device.port.DeviceLinkPort;
import com.ggec.earbuds.domain.device.port.Subscription;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 活动设备会话：一次只有一台设备在线。
 *
 * <p>本类持有<b>代数</b>（generation）：每次打开或关闭都换代，上一代的终态回调一律丢弃。
 * 换设备时先断后连；"谁被取代"由这里判定，适配层不再做仲裁。</p>
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
    private DeviceTarget target;
    private Consumer<ConnectSettlement> pending;
    private volatile DeviceSnapshot current = DeviceSnapshot.idle();

    public DeviceSession(DeviceLinkPort port) {
        this.port = Objects.requireNonNull(port, "port");
        port.observe(this::onSnapshot);
    }

    public DeviceSnapshot current() {
        return current;
    }

    public Optional<DeviceTarget> target() {
        synchronized (lock) {
            return Optional.ofNullable(target);
        }
    }

    public Subscription observe(Consumer<DeviceSnapshot> listener) {
        Objects.requireNonNull(listener, "listener");
        listeners.add(listener);
        listener.accept(current);
        return () -> listeners.remove(listener);
    }

    /**
     * 打开一台设备。已连接别的设备时先断开；同一代之后的终态才回调。
     * 被取代的旧请求以 {@link DeviceFailure#REJECTED} 结算，不会被静默丢弃。
     */
    public void open(DeviceTarget next, Consumer<ConnectSettlement> onSettled) {
        Objects.requireNonNull(next, "next");
        Objects.requireNonNull(onSettled, "onSettled");
        final long generationNow;
        final Consumer<ConnectSettlement> superseded;
        final DeviceTarget previous;
        synchronized (lock) {
            generationNow = ++generation;
            superseded = pending;
            pending = onSettled;
            previous = target;
            target = next;
        }
        if (superseded != null) {
            superseded.accept(new ConnectSettlement.Failed(DeviceFailure.REJECTED));
        }
        port.disconnect();
        // 换设备时先替旧设备发一次 DISCONNECTED：台账据此把旧设备归档，而不是让它停在 LIVE。
        if (previous != null && !previous.is(next.id())) {
            onSnapshot(disconnected(previous));
        }
        port.connect(next, settlement -> settle(generationNow, onSettled, settlement));
    }

    /** 关闭当前会话；进行中的连接尝试以 {@link DeviceFailure#REJECTED} 结算。 */
    public void close() {
        final Consumer<ConnectSettlement> cancelled;
        synchronized (lock) {
            generation++;
            cancelled = pending;
            pending = null;
        }
        port.disconnect();
        if (cancelled != null) {
            cancelled.accept(new ConnectSettlement.Failed(DeviceFailure.REJECTED));
        }
    }

    private void settle(long generationNow, Consumer<ConnectSettlement> callback,
                        ConnectSettlement settlement) {        synchronized (lock) {
            if (generationNow != generation) {
                return;
            }
            if (pending == callback) {
                pending = null;
            }
        }
        callback.accept(settlement);
    }

    private void onSnapshot(DeviceSnapshot snapshot) {
        current = snapshot;
        for (Consumer<DeviceSnapshot> listener : listeners) {
            listener.accept(snapshot);
        }
    }

    /** 旧设备断开时的快照：身份仍属于旧设备，实时值一律未知。 */
    @NonNull
    private static DeviceSnapshot disconnected(@NonNull DeviceTarget previous) {
        return new DeviceSnapshot(ConnectionState.DISCONNECTED, previous.id().modelId(),
                BatterySnapshot.unknown(), WearState.UNKNOWN, "",
                previous.id().bleAddress(), previous.identity().bleName(),
                previous.identity().classicAddress(), previous.identity().classicName(),
                DeviceSnapshot.UNKNOWN_EAR_COLOR, new DeviceState(Map.of()),
                Optional.empty(), List.of());
    }
}
