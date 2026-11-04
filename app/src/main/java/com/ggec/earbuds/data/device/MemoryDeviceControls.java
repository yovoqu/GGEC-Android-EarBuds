package com.ggec.earbuds.data.device;

import com.ggec.earbuds.domain.device.model.DeviceControls;
import com.ggec.earbuds.domain.device.port.DeviceControlsPort;
import com.ggec.earbuds.domain.device.port.Subscription;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/** 进程内的控件写者。同一时刻只保留一份快照。 */
public final class MemoryDeviceControls implements DeviceControlsPort {

    private final Object lock = new Object();
    private final CopyOnWriteArrayList<Consumer<DeviceControls>> listeners = new CopyOnWriteArrayList<>();
    private DeviceControls current = DeviceControls.initial();

    @Override
    public DeviceControls current() {
        synchronized (lock) {
            return current;
        }
    }

    @Override
    public Subscription observe(Consumer<DeviceControls> listener) {
        listeners.add(listener);
        listener.accept(current());
        return () -> listeners.remove(listener);
    }

    @Override
    public void update(UnaryOperator<DeviceControls> change) {
        DeviceControls next;
        synchronized (lock) {
            next = change.apply(current);
            if (next.equals(current)) {
                return;
            }
            current = next;
        }
        for (Consumer<DeviceControls> listener : listeners) {
            listener.accept(next);
        }
    }
}
