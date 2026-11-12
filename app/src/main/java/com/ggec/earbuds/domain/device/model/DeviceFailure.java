package com.ggec.earbuds.domain.device.model;

/**
 * 设备能力的失败分类（APP 视角）。只分类，不带文案、不带协议码。
 *
 * <p>由 {@code biz_ggec_hs} 的 {@code HsControl.Failure} 一一映射而来，映射单点在
 * {@code provider/biz_ggec_hs/HsValueMapper}，穷尽分支、无兜底。</p>
 */
public enum DeviceFailure {
    BLUETOOTH_DISABLED,
    PERMISSION_DENIED,
    LOCATION_DISABLED,
    BLE_UNSUPPORTED,
    SCAN_THROTTLED,
    SCAN_FAILED,
    NOT_READY,
    TIMEOUT,
    REJECTED,
    UNSUPPORTED_CAPABILITY,
    DEVICE_ERROR,
    INTERNAL_ERROR
}
