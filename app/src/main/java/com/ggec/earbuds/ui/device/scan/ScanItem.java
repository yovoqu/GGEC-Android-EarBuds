package com.ggec.earbuds.ui.device.scan;

/**
 * 扫描列表项。卡片展示 BLE 名称、BLE 地址和信号；connecting 标识当前项是否正在连接。
 */
public record ScanItem(
        String modelId,
        String bleAddress,
        String bleName,
        String classicAddress,
        String classicName,
        int rssi,
        boolean connecting
) {

    public ScanItem(String modelId, String bleAddress, String bleName,
                    String classicAddress, String classicName, int rssi) {
        this(modelId, bleAddress, bleName, classicAddress, classicName, rssi, false);
    }
}
