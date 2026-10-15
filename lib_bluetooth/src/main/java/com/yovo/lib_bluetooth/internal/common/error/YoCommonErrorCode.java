package com.yovo.lib_bluetooth.internal.common.error;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 蓝牙通用模块错误码枚举。
 */
public enum YoCommonErrorCode implements YoErrorCode {
    BLUETOOTH_NOT_SUPPORTED,
    BLUETOOTH_DISABLED,
    PERMISSION_DENIED,
    OPERATION_CANCELLED,
    TIMEOUT,
    RELEASED,
    INVALID_ARGUMENT,
    INTERNAL_ERROR;

    @Override
    public String code() {
        return "YO_COMMON_" + name();
    }
}
