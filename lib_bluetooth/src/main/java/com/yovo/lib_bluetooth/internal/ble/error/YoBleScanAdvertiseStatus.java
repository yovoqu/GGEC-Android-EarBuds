package com.yovo.lib_bluetooth.internal.ble.error;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.model.ble.BleErrorCode;

/**
 * 扫描（{@code ScanCallback#onScanFailed}）与广播（{@code AdvertiseCallback#onStartFailure}）
 * 平台错误码的集中定义与公开错误码映射。数值与 Android framework 常量一致，
 * 放在 core 层以便领域代码不依赖 {@code android.bluetooth.*}。
 */
public final class YoBleScanAdvertiseStatus {

    public static final int SCAN_FAILED_ALREADY_STARTED = 1;
    public static final int SCAN_FAILED_APPLICATION_REGISTRATION_FAILED = 2;
    public static final int SCAN_FAILED_INTERNAL_ERROR = 3;
    public static final int SCAN_FAILED_FEATURE_UNSUPPORTED = 4;
    public static final int SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES = 5;
    public static final int SCAN_FAILED_SCANNING_TOO_FREQUENTLY = 6;

    public static final int ADVERTISE_FAILED_DATA_TOO_LARGE = 1;
    public static final int ADVERTISE_FAILED_TOO_MANY_ADVERTISERS = 2;
    public static final int ADVERTISE_FAILED_ALREADY_STARTED = 3;
    public static final int ADVERTISE_FAILED_INTERNAL_ERROR = 4;
    public static final int ADVERTISE_FAILED_FEATURE_UNSUPPORTED = 5;

    private YoBleScanAdvertiseStatus() {
    }

    @NonNull
    public static BleErrorCode toScanErrorCode(int errorCode) {
        switch (errorCode) {
            case SCAN_FAILED_FEATURE_UNSUPPORTED:
                return BleErrorCode.SCAN_FEATURE_UNSUPPORTED;
            case SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES:
                return BleErrorCode.SCAN_OUT_OF_RESOURCES;
            case SCAN_FAILED_SCANNING_TOO_FREQUENTLY:
                return BleErrorCode.SCAN_THROTTLED;
            case SCAN_FAILED_INTERNAL_ERROR:
                return BleErrorCode.SCAN_INTERNAL_ERROR;
            default:
                return BleErrorCode.SCAN_START_FAILED;
        }
    }

    @NonNull
    public static BleErrorCode toAdvertiseErrorCode(int errorCode) {
        switch (errorCode) {
            case ADVERTISE_FAILED_DATA_TOO_LARGE:
                return BleErrorCode.ADVERTISE_DATA_TOO_LARGE;
            case ADVERTISE_FAILED_FEATURE_UNSUPPORTED:
                return BleErrorCode.UNSUPPORTED_OPERATION;
            default:
                return BleErrorCode.ADVERTISE_FAILED;
        }
    }
}
