package com.ggec.earbuds.ui.device.scan;

import androidx.annotation.StringRes;

import com.ggec.earbuds.R;

/**
 * 扫描页一次性事件。扫描失败与「需要用户授权」共用同一条通道，由
 * {@link ScanViewModel} 判定；视图层只按 {@link #requiresPermissionRequest()} 决定
 * 是发起系统权限请求还是提示 {@link #messageRes()}，不再自行判断该不该开扫。
 */
public enum ScanEvent {

    /**
     * 扫描需要蓝牙权限。视图层发起请求，结果回给
     * {@link ScanViewModel#onPermissionAnswer(boolean)}；若被直接提示，用同一条权限文案。
     */
    PERMISSION_REQUIRED(R.string.device_scan_error_permission, true),

    BLUETOOTH_DISABLED(R.string.device_scan_error_bluetooth_disabled, false),

    PERMISSION_DENIED(R.string.device_scan_error_permission, false),

    LOCATION_DISABLED(R.string.device_scan_error_location_disabled, false),

    BLE_UNSUPPORTED(R.string.device_scan_error_ble_unsupported, false),

    SCAN_THROTTLED(R.string.device_scan_error_throttled, false),

    FAILED(R.string.device_scan_error_failed, false);

    private final int messageRes;
    private final boolean requiresPermissionRequest;

    ScanEvent(@StringRes int messageRes, boolean requiresPermissionRequest) {
        this.messageRes = messageRes;
        this.requiresPermissionRequest = requiresPermissionRequest;
    }

    @StringRes
    public int messageRes() {
        return messageRes;
    }

    public boolean requiresPermissionRequest() {
        return requiresPermissionRequest;
    }
}
