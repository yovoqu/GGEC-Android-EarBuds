package com.ggec.earbuds.ui.device.scan;

/**
 * 扫描列表的界面项。View 不再接触领域模型 ScanResult；variantId 对界面是不透明字符串，
 * 只用于回传给 ViewModel 发起连接。
 */
public record ScanItem(
        String modelId,
        String variantId,
        String name,
        String mac,
        int rssi,
        boolean paired
) {
}
