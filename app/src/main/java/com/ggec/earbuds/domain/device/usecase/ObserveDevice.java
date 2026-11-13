package com.ggec.earbuds.domain.device.usecase;

import com.ggec.earbuds.domain.device.model.DeviceSnapshot;
import com.ggec.earbuds.domain.device.model.DeviceTarget;
import com.ggec.earbuds.domain.device.port.Subscription;
import com.ggec.earbuds.domain.device.session.DeviceSession;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/** 读取并订阅活动会话的设备快照。 */
public final class ObserveDevice {

    private final DeviceSession session;

    public ObserveDevice(DeviceSession session) {
        this.session = Objects.requireNonNull(session, "session");
    }

    public DeviceSnapshot current() {
        return session.current();
    }

    public Optional<DeviceTarget> target() {
        return session.target();
    }

    public Subscription execute(Consumer<DeviceSnapshot> listener) {
        return session.observe(listener);
    }
}
