package com.yovo.biz_ggec_hs.model;

/**
 * 协议扫描失败原因。不暴露 lib_bluetooth 的错误码。
 *
 * <p>每个变体都由 {@code HsScanFailures} 对 {@code BleErrorCode} 的穷尽映射产生，
 * 没有生产者的占位分类不允许存在：新增变体必须同时指出 lib 侧的错误码来源。</p>
 */
public enum HsScanFailure {
    /** 蓝牙适配器被用户关闭。 */
    BLUETOOTH_DISABLED,
    /** 扫描或定位权限被拒。 */
    PERMISSION_DENIED,
    /** 系统定位服务关闭（Android 扫描前置条件）。 */
    LOCATION_DISABLED,
    /** 设备不具备 BLE 能力。 */
    BLE_UNSUPPORTED,
    /** 单位窗口内启动扫描次数超过系统限流。 */
    SCAN_THROTTLED,
    /** 平台拒绝启动扫描，且未给出可归类的具体原因。 */
    SCAN_START_FAILED,
    /** 平台不支持本次扫描所需能力（如硬件批量/过滤），降级重试后仍失败。 */
    SCAN_FEATURE_UNSUPPORTED,
    /** 平台资源不足（扫描器/硬件资源耗尽）。 */
    SCAN_OUT_OF_RESOURCES,
    /** lib 扫描内部错误。 */
    SCAN_INTERNAL_ERROR,
    /** 扫描启动时 BLE 客户端已被释放。 */
    RELEASED
}
