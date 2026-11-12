package com.ggec.earbuds.ui.device.scan;

/**
 * 扫描列表项。卡片只展示 BLE 名称、BLE 地址和信号；型号与经典身份留给连接，不出现在卡片上。
 */
public record ScanItem(
        String modelId,
        String bleAddress,
        String bleName,
        String classicAddress,
        String classicName,
        int rssi
) {
}
