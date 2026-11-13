package com.ggec.earbuds.data.device.roster;

import com.ggec.earbuds.data.device.json.DeviceRosterJson;
import com.ggec.earbuds.domain.device.model.DeviceFailure;
import com.ggec.earbuds.domain.device.model.DeviceRoster;
import com.ggec.earbuds.domain.device.port.DeviceRosterRepository;
import com.ggec.earbuds.domain.device.port.Subscription;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * 台账仓库：内存快照 + 单写者合并写盘。
 *
 * <p>变更在调用线程上原子生效并立即通知观察者（观察者应快速返回，不得回调仓库）；
 * 落盘交给我们注入的串行执行器，多次变更只写最后一次，主线程不碰 JSON/文件/fsync。</p>
 *
 * <p>冷启动读失败：台账以空集启动，坏文件挪到 {@code .bad} 保留现场，
 * 失败经 {@link #startupFailure()} 与 {@link #observeFailures(Consumer)} 上报，
 * 绝不静默清空。</p>
 */
public final class DeviceRosterRepositoryImpl implements DeviceRosterRepository {

    private final File file;
    private final DeviceRosterJson json;
    private final Executor io;
    private final Object lock = new Object();
    private final CopyOnWriteArrayList<Consumer<DeviceRoster>> listeners =
            new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<DeviceFailure>> failureListeners =
            new CopyOnWriteArrayList<>();
    private final AtomicReference<DeviceRoster> pending = new AtomicReference<>();
    private final AtomicBoolean flushScheduled = new AtomicBoolean();

    private volatile DeviceRoster current;
    private final DeviceFailure startupFailure;

    public DeviceRosterRepositoryImpl(File file, DeviceRosterJson json, Executor io) {
        this.file = Objects.requireNonNull(file, "file");
        this.json = Objects.requireNonNull(json, "json");
        this.io = Objects.requireNonNull(io, "io");
        DeviceRoster loaded = DeviceRoster.empty();
        DeviceFailure failure = null;
        try {
            loaded = json.read(file);
        } catch (IOException broken) {
            failure = DeviceFailure.STORE_FAILED;
            json.quarantine(file);
        }
        this.current = loaded;
        this.startupFailure = failure;
    }

    @Override
    public DeviceRoster current() {
        return current;
    }

    @Override
    public Subscription observe(Consumer<DeviceRoster> listener) {
        Objects.requireNonNull(listener, "listener");
        listeners.add(listener);
        listener.accept(current);
        return () -> listeners.remove(listener);
    }

    @Override
    public Subscription observeFailures(Consumer<DeviceFailure> listener) {
        Objects.requireNonNull(listener, "listener");
        failureListeners.add(listener);
        return () -> failureListeners.remove(listener);
    }

    @Override
    public Optional<DeviceFailure> startupFailure() {
        return Optional.ofNullable(startupFailure);
    }

    @Override
    public void update(UnaryOperator<DeviceRoster> change) {
        Objects.requireNonNull(change, "change");
        DeviceRoster next;
        synchronized (lock) {
            next = Objects.requireNonNull(change.apply(current), "change result");
            if (next.equals(current)) {
                return;
            }
            current = next;
            for (Consumer<DeviceRoster> listener : listeners) {
                listener.accept(next);
            }
        }
        scheduleFlush(next);
    }

    private void scheduleFlush(DeviceRoster roster) {
        pending.set(roster);
        if (flushScheduled.compareAndSet(false, true)) {
            io.execute(this::flush);
        }
    }

    private void flush() {
        flushScheduled.set(false);
        DeviceRoster target = pending.getAndSet(null);
        if (target == null) {
            return;
        }
        try {
            json.write(file, target);
        } catch (IOException failure) {
            for (Consumer<DeviceFailure> listener : failureListeners) {
                listener.accept(DeviceFailure.STORE_FAILED);
            }
        }
        DeviceRoster newer = pending.get();
        if (newer != null) {
            scheduleFlush(newer);
        }
    }
}
