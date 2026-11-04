package com.ggec.earbuds.domain.device.model;

import java.util.Objects;

/** SDK 归一化后的设备标准状态；APP 只读。 */
public record DeviceSnapshot(
        ConnectionState connection,
        String modelId,
        String variantId,
        BatterySnapshot battery,
        WearState wear,
        String firmware,
        String deviceName,
        String mac,
        String colorLabel
) {

    public DeviceSnapshot {
        Objects.requireNonNull(connection, "connection");
        battery = battery == null ? BatterySnapshot.unknown() : battery;
        wear = wear == null ? WearState.NOT_WORN : wear;
        firmware = firmware == null ? "" : firmware;
        deviceName = deviceName == null ? "" : deviceName;
        mac = mac == null ? "" : mac;
        colorLabel = colorLabel == null ? "" : colorLabel;
    }

    public static DeviceSnapshot idle() {
        return new DeviceSnapshot(ConnectionState.IDLE, null, null,
                BatterySnapshot.unknown(), WearState.NOT_WORN, "", "", "", "");
    }

    public boolean isConnected() {
        return connection.isConnected();
    }
}
