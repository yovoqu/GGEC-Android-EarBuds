package com.ggec.earbuds.ui.device.scan;

/**
 * 扫描列表项。名称和地址都是 BLE 扫描观察，不是经典蓝牙名称或 BD_ADDR。
 * variantId 对界面不透明，只回传给 ViewModel 发起连接。
 */
public record ScanItem(
        String modelId,
        String variantId,
        String bleName,
        String bleMac,
        int rssi
) {
}
