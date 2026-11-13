package com.yovo.biz_ggec_hs.internal.center.port;

/** 扫描链路失败。蓝牙库错误码只在适配层收成这里，公开失败分类在扫描实现里再转。 */
public enum HsScanFault {
    BLUETOOTH_DISABLED,
    PERMISSION_DENIED,
    LOCATION_DISABLED,
    BLE_UNSUPPORTED,
    SCAN_THROTTLED,
    SCAN_START_FAILED,
    SCAN_FEATURE_UNSUPPORTED,
    SCAN_OUT_OF_RESOURCES,
    SCAN_INTERNAL_ERROR,
    RELEASED
}
