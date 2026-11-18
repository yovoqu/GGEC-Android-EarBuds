package com.ggec.earbuds.domain.device.model;

import java.util.Objects;

/**
 * 扫描发现的设备结果。
 * 上层只面向设备型号、设备名称和 MAC 地址，由 Biz 侧负责数据提取与映射，
 * 严禁上层感知或绑定 BLE / 经典蓝牙细节。
 */
public record ScanResult(
        String modelId,
        String name,
        String address,
        int rssi,
        String productId
) {

    public ScanResult {
        Objects.requireNonNull(modelId, "modelId");
        name = name != null ? name.trim() : "";
        address = address != null ? address.trim().toUpperCase(java.util.Locale.ROOT) : "";
        productId = productId != null ? productId.trim() : "";
        if (modelId.isEmpty() || address.isEmpty()) {
            throw new IllegalArgumentException("ScanResult requires modelId and address");
        }
    }

    /** 兼容旧构造：没有 Product ID 的来源用空串。 */
    public ScanResult(String modelId, String name, String address, int rssi) {
        this(modelId, name, address, rssi, "");
    }

    /** 设备展示名称 */
    public String displayName() {
        return !name.isEmpty() ? name : modelId;
    }

    /** 设备 MAC 地址展示 */
    public String displayAddress() {
        return address.replace('-', ':');
    }

    /** 同一台设备根据型号与 MAC 地址比对去重 */
    public boolean sameDevice(ScanResult other) {
        return other != null
                && modelId.equals(other.modelId)
                && address.equalsIgnoreCase(other.address);
    }
}
