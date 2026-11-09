package com.yovo.lib_bluetooth.internal.ble.internal.android.hiddenapi;

/**
 * BLE 隐藏反射 API 调用策略控制（默认 DISABLED，仅显式授权 EXPLICITLY_ENABLED 时放行）。
 */
public enum YoBleHiddenApiPolicy {
    DISABLED,
    EXPLICITLY_ENABLED
}
