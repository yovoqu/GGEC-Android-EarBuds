package com.ggec.earbuds.domain.record.model;

/**
 * 扫描失败分类。只表达类别；面向用户的文案由 UI 层经 strings 资源映射，不进入领域层。
 */
public enum RecordScanFailure {
    BLUETOOTH_DISABLED,
    PERMISSION_DENIED,
    BLE_UNSUPPORTED,
    THROTTLED,
    FAILED
}
