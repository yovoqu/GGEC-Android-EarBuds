package com.ggec.earbuds.ui.device.scan;

import java.util.Objects;

/**
 * 扫描设备列表项。
 * 纯粹面向 UI 展示：型号、名称、MAC 地址、信号强度与连接中状态。
 */
public record ScanItem(
        String modelId,
        String name,
        String address,
        int rssi,
        boolean connecting
) {

    public ScanItem {
        Objects.requireNonNull(modelId, "modelId");
        name = name != null ? name.trim() : "";
        address = address != null ? address.trim().toUpperCase(java.util.Locale.ROOT) : "";
    }

    public ScanItem(String modelId, String name, String address, int rssi) {
        this(modelId, name, address, rssi, false);
    }

    /** 设备展示名称 */
    public String displayName() {
        return !name.isEmpty() ? name : modelId;
    }

    /** 设备 MAC 地址展示 */
    public String displayAddress() {
        return address.replace('-', ':');
    }
}
