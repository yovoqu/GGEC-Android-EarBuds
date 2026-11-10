package com.yovo.lib_bluetooth.internal.ble.scan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.model.ble.BleScanConfig;
import com.yovo.lib_bluetooth.model.ble.BleScanResult;

import java.util.List;

/**
 * BLE 扫描平台适配端口，封装 Android BluetoothLeScanner 底层交互。
 */
public interface YoBleScanPlatform {

    interface PlatformCallback {
        void onScanResult(@NonNull BleScanResult result);

        void onBatchScanResults(@NonNull List<BleScanResult> results);

        void onPlatformScanFailed(int rawErrorCode, @Nullable String detail);
    }

    /**
     * 启动底层平台扫描。{@link StartResult#ok()} 表示平台已接受。
     */
    @NonNull
    StartResult startPlatformScan(@NonNull BleScanConfig config, @NonNull PlatformCallback callback);

    final class StartResult {
        public final boolean success;
        public final int rawCode;
        @Nullable
        public final String detail;

        private StartResult(boolean success, int rawCode, @Nullable String detail) {
            this.success = success;
            this.rawCode = rawCode;
            this.detail = detail;
        }

        @NonNull
        public static StartResult ok() {
            return new StartResult(true, 0, null);
        }

        @NonNull
        public static StartResult failed(int rawCode, @NonNull String detail) {
            return new StartResult(false, rawCode, detail);
        }
    }

    void stopPlatformScan();

    boolean flushPendingScanResults();

    /** 控制器能否把 {@code reportDelay} 下推到硬件批量扫描。 */
    boolean supportsHardwareBatching();
}
