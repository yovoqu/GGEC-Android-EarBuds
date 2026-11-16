package com.yovo.biz_ggec_record.model;

import androidx.annotation.NonNull;

/**
 * 扫描失败原因（面向 UI 的稳定枚举，不含蓝牙库错误码）。
 */
public enum RecordScanFailure {
    BLUETOOTH_DISABLED,
    PERMISSION_DENIED,
    BLE_UNSUPPORTED,
    THROTTLED,
    FAILED;

    @NonNull
    public String message() {
        return switch (this) {
            case BLUETOOTH_DISABLED -> "蓝牙未开启";
            case PERMISSION_DENIED -> "缺少蓝牙权限";
            case BLE_UNSUPPORTED -> "设备不支持 BLE";
            case THROTTLED -> "扫描过于频繁";
            case FAILED -> "扫描失败";
        };
    }
}
