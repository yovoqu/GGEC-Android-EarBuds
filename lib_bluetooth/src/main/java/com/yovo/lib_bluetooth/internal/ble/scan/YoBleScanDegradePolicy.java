package com.yovo.lib_bluetooth.internal.ble.scan;

import com.yovo.lib_bluetooth.internal.ble.error.YoBleScanAdvertiseStatus;

/**
 * 平台扫描失败的降级重试判定：仅对“硬件不支持 / 硬件资源不足”在每代扫描内降级重试一次，
 * 对齐 Nordic Android-Scanner-Compat-Library 的降级思路。
 */
final class YoBleScanDegradePolicy {

    private YoBleScanDegradePolicy() {
    }

    static boolean isDegradable(int rawErrorCode) {
        return rawErrorCode == YoBleScanAdvertiseStatus.SCAN_FAILED_FEATURE_UNSUPPORTED
                || rawErrorCode == YoBleScanAdvertiseStatus.SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES;
    }

    static boolean shouldRetryOnce(int rawErrorCode, long generation, long degradedRetryGeneration) {
        return isDegradable(rawErrorCode) && degradedRetryGeneration != generation;
    }
}
