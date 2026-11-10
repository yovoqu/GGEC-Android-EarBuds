package com.yovo.lib_bluetooth.internal.ble.internal.android;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.ble.internal.android.compat.YoBleScanCompat;
import com.yovo.lib_bluetooth.internal.ble.scan.YoBleScanPlatform;
import com.yovo.lib_bluetooth.internal.ble.scan.YoBleScanPlatform.StartResult;
import com.yovo.lib_bluetooth.internal.common.bluetooth.YoBluetoothAdapterProvider;
import com.yovo.lib_bluetooth.internal.common.diagnostics.YoBtLogger;
import com.yovo.lib_bluetooth.model.ble.BleScanConfig;
import com.yovo.lib_bluetooth.model.ble.BleScanResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Android {@link BluetoothLeScanner} 平台适配器（含回调生成期隔离与低端 ROM 扫描参数自动降级）。
 */
@SuppressLint("MissingPermission")
public final class YoBleAndroidScanPlatform implements YoBleScanPlatform {

    private static final String TAG = "YoBleAndroidScan";

    private final YoBluetoothAdapterProvider adapterProvider;
    private final Object scanLock = new Object();
    private final AtomicLong generationSeq = new AtomicLong();

    private volatile long activeGeneration = 0L;
    @Nullable
    private volatile ScanCallback activeCallback;
    @Nullable
    private volatile BluetoothLeScanner activeScanner;

    public YoBleAndroidScanPlatform(@NonNull YoBluetoothAdapterProvider adapterProvider) {
        this.adapterProvider = adapterProvider;
    }

    @Override
    public boolean supportsHardwareBatching() {
        BluetoothAdapter adapter = adapterProvider.adapter();
        return adapter != null && adapter.isOffloadedScanBatchingSupported();
    }

    @NonNull
    @Override
    public YoBleScanPlatform.StartResult startPlatformScan(@NonNull BleScanConfig config,
                                         @NonNull PlatformCallback callback) {
        BluetoothAdapter adapter = adapterProvider.adapter();
        if (adapter == null || !adapter.isEnabled()) {
            return StartResult.failed(0, "Bluetooth adapter unavailable or disabled");
        }
        BluetoothLeScanner scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            return StartResult.failed(0, "BluetoothLeScanner unavailable");
        }

        synchronized (scanLock) {
            stopPlatformScanLocked();

            final long gen = generationSeq.incrementAndGet();
            ScanSettings settings = YoBleScanCompat.toAndroidScanSettings(config);
            List<ScanFilter> filters = YoBleScanCompat.toAndroidScanFilters(config.filters());

            ScanCallback androidCallback = new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    if (gen != activeGeneration || activeCallback != this || result == null) {
                        return;
                    }
                    BleScanResult mapped = YoBleScanCompat.toBleScanResult(
                            result, callbackType, System.currentTimeMillis());
                    callback.onScanResult(mapped);
                }

                @Override
                public void onBatchScanResults(List<ScanResult> results) {
                    if (gen != activeGeneration || activeCallback != this
                            || results == null || results.isEmpty()) {
                        return;
                    }
                    long now = System.currentTimeMillis();
                    List<BleScanResult> mapped = new ArrayList<>(results.size());
                    for (ScanResult r : results) {
                        if (r != null) {
                            mapped.add(YoBleScanCompat.toBleScanResult(
                                    r, ScanSettings.CALLBACK_TYPE_ALL_MATCHES, now));
                        }
                    }
                    if (!mapped.isEmpty()) {
                        callback.onBatchScanResults(mapped);
                    }
                }

                @Override
                public void onScanFailed(int errorCode) {
                    if (gen != activeGeneration || activeCallback != this) {
                        return;
                    }
                    callback.onPlatformScanFailed(errorCode, "ScanCallback.onScanFailed(" + errorCode + ")");
                }
            };

            activeGeneration = gen;
            activeCallback = androidCallback;
            activeScanner = scanner;
            try {
                if (filters.isEmpty()) {
                    scanner.startScan(null, settings, androidCallback);
                } else {
                    scanner.startScan(filters, settings, androidCallback);
                }
                return StartResult.ok();
            } catch (IllegalArgumentException unsupportedParam) {
                YoBtLogger.w(TAG, "Primary startScan rejected args, falling back to software filter scan",
                        unsupportedParam);
                try {
                    ScanSettings fallbackSettings = YoBleScanCompat.toFallbackScanSettings(config);
                    scanner.startScan(null, fallbackSettings, androidCallback);
                    return StartResult.ok();
                } catch (Throwable fallbackErr) {
                    activeCallback = null;
                    activeScanner = null;
                    return StartResult.failed(0, "BluetoothLeScanner.startScan failed: " + fallbackErr.getMessage());
                }
            } catch (Throwable t) {
                activeCallback = null;
                activeScanner = null;
                return StartResult.failed(0, "BluetoothLeScanner.startScan failed: " + t.getMessage());
            }
        }
    }

    @Override
    public boolean flushPendingScanResults() {
        synchronized (scanLock) {
            BluetoothLeScanner scanner = activeScanner;
            ScanCallback callback = activeCallback;
            if (scanner != null && callback != null) {
                try {
                    scanner.flushPendingScanResults(callback);
                    return true;
                } catch (Throwable ignored) {
                    return false;
                }
            }
            return false;
        }
    }

    @Override
    public void stopPlatformScan() {
        synchronized (scanLock) {
            stopPlatformScanLocked();
        }
    }

    private void stopPlatformScanLocked() {
        generationSeq.incrementAndGet();
        activeGeneration = generationSeq.get();
        ScanCallback cb = activeCallback;
        BluetoothLeScanner scanner = activeScanner;
        activeCallback = null;
        activeScanner = null;
        if (scanner != null && cb != null) {
            try {
                scanner.stopScan(cb);
            } catch (Throwable ignored) {
            }
        }
    }
}
