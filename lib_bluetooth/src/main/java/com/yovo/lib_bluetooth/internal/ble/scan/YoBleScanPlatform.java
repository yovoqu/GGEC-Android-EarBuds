package com.yovo.lib_bluetooth.internal.ble.scan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.model.ble.BleScanResult;

import java.util.List;

/**
 * BLE 扫描平台适配端口，封装 Android BluetoothLeScanner 底层交互。
 *
 * <p>异常契约：实现自行捕获并记录底层异常（经 {@code YoBtLogger}），<b>不得向调用方抛出</b>；
 * 失败一律通过 {@link StartResult}、布尔返回值或 {@link PlatformCallback#onPlatformScanFailed}
 * 分类返回。调用方（控制器）因此不做二次 try/catch——平台失败的原因只在本实现里记录，避免
 * “吞异常”和“重复包裹”两种相反的错误。</p>
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

    /** 平台是否支持硬件卸载过滤（不支持时只能无过滤扫描 + 客户端按帧过滤）。 */
    boolean supportsOffloadedFiltering();

    /** 平台是否支持 LE 扩展广播。 */
    boolean supportsExtendedAdvertising();
}
