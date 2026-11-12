package com.ggec.earbuds.domain.device.model;

import java.util.Objects;

/**
 * 扫描页上的一台双模设备。BLE 地址用于连接，经典地址是另一套身份。
 * 广播没带的字段由 biz 写成空串。
 */
public record ScanResult(
        String modelId,
        String bleAddress,
        String bleName,
        String classicAddress,
        String classicName,
        int rssi
) {

    public ScanResult {
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(bleAddress, "bleAddress");
        Objects.requireNonNull(bleName, "bleName");
        Objects.requireNonNull(classicAddress, "classicAddress");
        Objects.requireNonNull(classicName, "classicName");
    }

    /** {@code other} 非空：同一台设备看 BLE 地址和型号，不拿经典地址混比。 */
    public boolean sameDevice(ScanResult other) {
        return bleAddress.equalsIgnoreCase(Objects.requireNonNull(other, "other").bleAddress)
                && modelId.equals(other.modelId);
    }
}
