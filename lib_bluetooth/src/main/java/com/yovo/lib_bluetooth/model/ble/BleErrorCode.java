package com.yovo.lib_bluetooth.model.ble;

/**
 * BLE 公开错误码，完整覆盖硬件/权限/扫描/HCI/ATT/GATT/传输/配对/L2CAP/外设/生命周期各阶段。
 */
public enum BleErrorCode {
    // ── 硬件与系统状态 ─────────────────────────────────────────────
    BLUETOOTH_NOT_SUPPORTED,
    BLE_NOT_SUPPORTED,
    BLUETOOTH_DISABLED,
    LOCATION_SERVICES_DISABLED,
    UNSUPPORTED_OPERATION,

    // ── 运行时权限 ─────────────────────────────────────────────────
    PERMISSION_SCAN_DENIED,
    PERMISSION_CONNECT_DENIED,
    PERMISSION_ADVERTISE_DENIED,
    PERMISSION_LOCATION_DENIED,

    // ── 扫描 ───────────────────────────────────────────────────────
    SCAN_START_FAILED,
    SCAN_STOP_FAILED,
    SCAN_THROTTLED,
    SCAN_FEATURE_UNSUPPORTED,
    SCAN_OUT_OF_RESOURCES,
    SCAN_INTERNAL_ERROR,

    // ── 设备与配置校验 ─────────────────────────────────────────────
    DEVICE_INVALID,
    ILLEGAL_ARGUMENT,
    ILLEGAL_STATE,

    // ── 连接与 HCI 链路 ───────────────────────────────────────────
    CONNECT_FAILED,
    CONNECT_TIMEOUT,
    LINK_LOSS,
    REMOTE_TERMINATED,
    LOCAL_TERMINATED,
    GATT_ERROR_133,
    RETRIES_EXHAUSTED,
    CONNECTION_NOT_READY,

    // ── GATT 操作与 ATT 协议 ───────────────────────────────────────
    GATT_OPERATION_TIMEOUT,
    GATT_BUSY,
    GATT_QUEUE_FULL,
    SERVICE_DISCOVERY_FAILED,
    SERVICE_NOT_FOUND,
    CHARACTERISTIC_NOT_FOUND,
    DESCRIPTOR_NOT_FOUND,
    READ_FAILED,
    WRITE_FAILED,
    NOTIFY_FAILED,
    AUTHENTICATION_FAILED,
    AUTHORIZATION_FAILED,
    ENCRYPTION_FAILED,
    INVALID_OFFSET_OR_LENGTH,
    REQUEST_NOT_SUPPORTED,
    RELIABLE_WRITE_MISMATCH,

    // ── 长包分片与请求响应 ─────────────────────────────────────────
    TRANSFER_FAILED,
    RESPONSE_MISMATCH,
    PAYLOAD_MERGE_FAILED,

    // ── 配对与隐藏 API ─────────────────────────────────────────────
    BOND_FAILED,
    BOND_CANCELLED,
    HIDDEN_API_BLOCKED,
    HIDDEN_API_FAILED,

    // ── L2CAP & 外设广播 / GATT Server ────────────────────────────
    L2CAP_OPEN_FAILED,
    L2CAP_IO_FAILED,
    ADVERTISE_FAILED,
    ADVERTISE_DATA_TOO_LARGE,
    GATT_SERVER_START_FAILED,
    GATT_SERVER_NOTIFY_FAILED,

    // ── 生命周期 ───────────────────────────────────────────────────
    OPERATION_CANCELLED,
    RELEASED,
    INTERNAL_ERROR
}
