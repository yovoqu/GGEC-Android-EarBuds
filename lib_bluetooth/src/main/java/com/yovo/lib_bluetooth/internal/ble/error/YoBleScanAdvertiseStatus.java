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
    public static String describeScan(int errorCode) {
        switch (errorCode) {
            case SCAN_FAILED_ALREADY_STARTED: return "SCAN_FAILED_ALREADY_STARTED (1)";
            case SCAN_FAILED_APPLICATION_REGISTRATION_FAILED:
                return "SCAN_FAILED_APPLICATION_REGISTRATION_FAILED (2)";
            case SCAN_FAILED_INTERNAL_ERROR: return "SCAN_FAILED_INTERNAL_ERROR (3)";
            case SCAN_FAILED_FEATURE_UNSUPPORTED: return "SCAN_FAILED_FEATURE_UNSUPPORTED (4)";
            case SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES: return "SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES (5)";
            case SCAN_FAILED_SCANNING_TOO_FREQUENTLY: return "SCAN_FAILED_SCANNING_TOO_FREQUENTLY (6)";
            default: return "SCAN_FAILED_" + errorCode;
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

    @NonNull
    public static String describeAdvertise(int errorCode) {
        switch (errorCode) {
            case ADVERTISE_FAILED_DATA_TOO_LARGE: return "ADVERTISE_FAILED_DATA_TOO_LARGE (1)";
            case ADVERTISE_FAILED_TOO_MANY_ADVERTISERS: return "ADVERTISE_FAILED_TOO_MANY_ADVERTISERS (2)";
            case ADVERTISE_FAILED_ALREADY_STARTED: return "ADVERTISE_FAILED_ALREADY_STARTED (3)";
            case ADVERTISE_FAILED_INTERNAL_ERROR: return "ADVERTISE_FAILED_INTERNAL_ERROR (4)";
            case ADVERTISE_FAILED_FEATURE_UNSUPPORTED: return "ADVERTISE_FAILED_FEATURE_UNSUPPORTED (5)";
            default: return "ADVERTISE_FAILED_" + errorCode;
        }
    }
}
