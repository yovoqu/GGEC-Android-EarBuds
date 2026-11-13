package com.ggec.earbuds.testing.device;

import com.ggec.earbuds.domain.device.model.DeviceFailure;
import com.ggec.earbuds.domain.device.model.DeviceRoster;
import com.ggec.earbuds.domain.device.port.DeviceRosterRepository;
import com.ggec.earbuds.domain.device.port.Subscription;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/** 内存台账仓库：只做内存发布，不落盘；用于领域用例测试。 */
public final class MemoryDeviceRosterRepository implements DeviceRosterRepository {

    private final List<Consumer<DeviceRoster>> listeners = new ArrayList<>();
    private final List<Consumer<DeviceFailure>> failureListeners = new ArrayList<>();
    private DeviceRoster roster;

    public MemoryDeviceRosterRepository() {
        this(DeviceRoster.empty());
    }

    public MemoryDeviceRosterRepository(DeviceRoster roster) {
        this.roster = roster;
    }

    @Override
    public DeviceRoster current() {
        return roster;
    }

    @Override
    public Subscription observe(Consumer<DeviceRoster> listener) {
        listeners.add(listener);
        listener.accept(roster);
        return () -> listeners.remove(listener);
    }

    @Override
    public Subscription observeFailures(Consumer<DeviceFailure> listener) {
        failureListeners.add(listener);
        return () -> failureListeners.remove(listener);
    }

    @Override
    public Optional<DeviceFailure> startupFailure() {
        return Optional.empty();
    }

    @Override
    public void update(UnaryOperator<DeviceRoster> change) {
        DeviceRoster next = change.apply(roster);
        if (next.equals(roster)) {
            return;
        }
        roster = next;
        for (Consumer<DeviceRoster> listener : List.copyOf(listeners)) {
            listener.accept(next);
        }
    }

    public void fail(DeviceFailure failure) {
        for (Consumer<DeviceFailure> listener : List.copyOf(failureListeners)) {
            listener.accept(failure);
        }
    }
}
