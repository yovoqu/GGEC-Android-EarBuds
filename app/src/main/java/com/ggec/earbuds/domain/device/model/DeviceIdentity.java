package com.ggec.earbuds.domain.device.model;

import java.util.Objects;

/**
 * 双模设备的其余身份字段。空串表示这一侧还没上报，不得用另一侧的值填上。
 *
 * <p>{@code bleAddress} 在 {@link DeviceId} 里，这里只放名称与经典地址。</p>
 */
public record DeviceIdentity(String bleName, String classicAddress, String classicName) {

    public DeviceIdentity {
        bleName = Objects.requireNonNull(bleName, "bleName");
        classicAddress = Objects.requireNonNull(classicAddress, "classicAddress");
        classicName = Objects.requireNonNull(classicName, "classicName");
    }

    public static DeviceIdentity unknown() {
        return new DeviceIdentity("", "", "");
    }

    /** 经典地址未知时无法建立双模链路（需要先连经典音频）。 */
    public boolean hasClassicAddress() {
        return !classicAddress.isEmpty();
    }
}
