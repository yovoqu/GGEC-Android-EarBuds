package com.yovo.lib_bluetooth.model.ble;

/**
 * BLE 扫描结果回调类型（支持跨版本软硬件协同仿真 FIRST_MATCH / MATCH_LOST）。
 */
public enum BleScanCallbackType {
    ALL_MATCHES,
    FIRST_MATCH,
    MATCH_LOST,
    FIRST_MATCH_AND_LOST
}
