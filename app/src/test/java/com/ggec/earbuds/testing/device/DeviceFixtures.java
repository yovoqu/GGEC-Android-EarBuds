package com.ggec.earbuds.testing.device;

import com.ggec.earbuds.domain.device.model.BatterySnapshot;
import com.ggec.earbuds.domain.device.model.ConnectionState;
import com.ggec.earbuds.domain.device.model.ControlValue;
import com.ggec.earbuds.domain.device.model.DeviceId;
import com.ggec.earbuds.domain.device.model.DeviceIdentity;
import com.ggec.earbuds.domain.device.model.DeviceSnapshot;
import com.ggec.earbuds.domain.device.model.DeviceState;
import com.ggec.earbuds.domain.device.model.DeviceTarget;
import com.ggec.earbuds.domain.device.model.WearState;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 设备域测试共用的快照/目标构造。 */
public final class DeviceFixtures {

    public static final DeviceId ID = new DeviceId("HS01", "AA:BB:CC:DD:EE:FF");

    private DeviceFixtures() {
    }

    public static DeviceTarget target() {
        return new DeviceTarget(ID, new DeviceIdentity("HS01", "11:22:33:44:55:66", "HS01"));
    }

    public static DeviceTarget targetWithoutClassic() {
        return new DeviceTarget(ID, new DeviceIdentity("HS01", "", ""));
    }

    public static DeviceSnapshot ready(String firmware, int leftBattery) {
        return snapshot(ConnectionState.READY, firmware, leftBattery, new DeviceState(
                Map.of("EQ_MODE", new ControlValue.Choice("POP"))));
    }

    public static DeviceSnapshot connecting() {
        return snapshot(ConnectionState.CONNECTING, "", BatterySnapshot.UNKNOWN, new DeviceState(Map.of()));
    }

    public static DeviceSnapshot disconnected() {
        return snapshot(ConnectionState.DISCONNECTED, "", BatterySnapshot.UNKNOWN, new DeviceState(Map.of()));
    }

    private static DeviceSnapshot snapshot(ConnectionState connection, String firmware,
                                           int leftBattery, DeviceState state) {
        return new DeviceSnapshot(connection, ID.modelId(),
                new BatterySnapshot(leftBattery, leftBattery, BatterySnapshot.UNKNOWN, false),
                WearState.UNKNOWN, firmware,
                ID.bleAddress(), "HS01", "11:22:33:44:55:66", "HS01",
                3, state, Optional.empty(), List.of());
    }
}
