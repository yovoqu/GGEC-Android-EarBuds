package com.ggec.earbuds.domain.device.model;

import java.util.Objects;

/**
 * 只读设备事实：电量、固件、佩戴、耳色。
 *
 * <p>缺失一律用显式未知值（{@link BatterySnapshot#unknown()}、空串、
 * {@link WearState#UNKNOWN}、{@link DeviceSnapshot#UNKNOWN_EAR_COLOR}），
 * 不用 0 或界面文案冒充。</p>
 */
public record DeviceFacts(BatterySnapshot battery, String firmware,
                          WearState wearState, int earColor) {

    public DeviceFacts {
        Objects.requireNonNull(battery, "battery");
        firmware = Objects.requireNonNull(firmware, "firmware");
        Objects.requireNonNull(wearState, "wearState");
    }

    public static DeviceFacts unknown() {
        return new DeviceFacts(BatterySnapshot.unknown(), "",
                WearState.UNKNOWN, DeviceSnapshot.UNKNOWN_EAR_COLOR);
    }

    /** 有没有任何设备事实。这是"快照够不够渲染信息"的唯一回答（取代已删除的 DeviceIntake）。 */
    public boolean known() {
        return battery.hasLeft() || battery.hasRight() || battery.hasCase()
                || !firmware.isEmpty();
    }
}
