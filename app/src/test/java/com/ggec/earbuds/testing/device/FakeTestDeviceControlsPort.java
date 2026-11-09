package com.ggec.earbuds.testing.device;

import com.ggec.earbuds.domain.device.model.DeviceControls;
import com.ggec.earbuds.domain.device.port.DeviceControlsPort;
import com.ggec.earbuds.domain.device.port.Subscription;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/** 单测用设备控件端口：可配置是否可用及记录变更次数。 */
public final class FakeTestDeviceControlsPort implements DeviceControlsPort {

    private final CopyOnWriteArrayList<Consumer<DeviceControls>> listeners = new CopyOnWriteArrayList<>();
    private final boolean available;
    private DeviceControls current;
    public int updateCalls;

    public FakeTestDeviceControlsPort(boolean available, DeviceControls initial) {
        this.available = available;
        this.current = initial;
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public DeviceControls current() {
        return current;
    }

    @Override
    public Subscription observe(Consumer<DeviceControls> listener) {
        listeners.add(listener);
        listener.accept(current);
        return () -> listeners.remove(listener);
    }

    @Override
    public void update(UnaryOperator<DeviceControls> change) {
        updateCalls++;
        if (!available) {
            return;
        }
        DeviceControls next = change.apply(current);
        if (next.equals(current)) {
            return;
        }
        current = next;
        for (Consumer<DeviceControls> listener : listeners) {
            listener.accept(next);
        }
    }
}
