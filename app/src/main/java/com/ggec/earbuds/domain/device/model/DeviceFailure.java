package com.ggec.earbuds.domain.device.model;

/**
 * 设备能力的失败分类（APP 视角）。只分类，不带文案、不带协议码。
 *
 * <p>由 {@code biz_ggec_hs} 的 {@code HsControl.Failure} 一一映射而来，映射单点在
 * {@code provider/biz_ggec_hs/HsValueMapper}，穷尽分支、无兜底。</p>
 *
 * <p>{@link #INCOMPLETE_IDENTITY}、{@link #STORE_FAILED}、{@link #DEVICE_NOT_FOUND}
 * 只由 APP 侧产生（扫描结果缺经典地址、台账读写失败、操作了不存在的设备），不从 biz 下发给你的
 * 失败里出现。</p>
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
    INTERNAL_ERROR,
    /** 扫描结果没有经典地址：双模链路的第一步就缺参数。 */
    INCOMPLETE_IDENTITY,
    /** 台账或档案读写失败（含 JSON 损坏）。 */
    STORE_FAILED,
    /** 目标设备不在台账里。 */
    DEVICE_NOT_FOUND
}
