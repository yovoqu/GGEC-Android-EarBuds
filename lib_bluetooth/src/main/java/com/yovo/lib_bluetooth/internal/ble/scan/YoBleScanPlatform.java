package com.yovo.lib_bluetooth.internal.ble.scan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.model.ble.BleScanConfig;
import com.yovo.lib_bluetooth.model.ble.BleScanResult;

import java.util.List;

/**
 * BLE 扫描平台适配端口（隔离 Android BluetoothLeScanner 与测试用 Fake）。
 */
public interface YoBleScanPlatform {

    interface PlatformCallback {
        void onScanResult(@NonNull BleScanResult result);

        void onBatchScanResults(@NonNull List<BleScanResult> results);

        void onPlatformScanFailed(int rawErrorCode, @Nullable String detail);
    }

    /**
     * 启动底层平台扫描；成功返回 null 或抛出受控错误信息由返回值表达（null 表示成功）。
     */
    @Nullable
    String startPlatformScan(@NonNull BleScanConfig config, @NonNull PlatformCallback callback);

    void stopPlatformScan();

    boolean flushPendingScanResults();
}
