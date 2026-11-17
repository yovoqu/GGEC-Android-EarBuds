package com.yovo.lib_bluetooth.internal.ble.internal.android;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.ble.internal.android.compat.YoBleScanCompat;
import com.yovo.lib_bluetooth.internal.ble.scan.BleScanConfig;
import com.yovo.lib_bluetooth.internal.ble.scan.YoBleScanPlatform;
import com.yovo.lib_bluetooth.internal.ble.scan.YoBleScanPlatform.StartResult;
import com.yovo.lib_bluetooth.internal.common.bluetooth.YoBluetoothAdapterProvider;
import com.yovo.lib_bluetooth.internal.common.bluetooth.radio.YoBtRadioCoordinator;
import com.yovo.lib_bluetooth.internal.common.bluetooth.radio.YoBtRadioLease;
import com.yovo.lib_bluetooth.model.ble.BleScanResult;
import com.yovo.lib_bluetooth.provider.lib_common.log.YoBtLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Android {@link BluetoothLeScanner} 平台适配器（含回调生成期隔离、低端 ROM 扫描参数自动降级与射频仲裁协同）。
 */
@SuppressLint("MissingPermission")
public final class YoBleAndroidScanPlatform implements YoBleScanPlatform {

    private static final String TAG = "YoBleAndroidScan";

    private final YoBluetoothAdapterProvider adapterProvider;
    private final YoBtRadioCoordinator radioCoordinator;
    private final Object scanLock = new Object();
    private final AtomicLong generationSeq = new AtomicLong();

    private volatile long activeGeneration = 0L;
    @Nullable
    private volatile ScanCallback activeCallback;
    @Nullable
    private volatile BluetoothLeScanner activeScanner;
    @Nullable
    private volatile YoBtRadioLease activeRadioLease;
    @Nullable
    private volatile BleScanConfig activeConfig;
    @Nullable
    private volatile PlatformCallback activePlatformCallback;
    private volatile boolean scanSuspendedByRadio = false;

    /**
     * 扫描结果转换专用线程（主线程保护）：Android 的 {@link ScanCallback} 默认在主线程/Looper 回调，
     * 而 {@link YoBleScanCompat#toBleScanResult} 会做 AD TLV 解析与多个同步 Binder IPC
     * （getName/getAlias/getType/getAddressType）。把转换移出主线程，回调本身只做一次轻量入队，
     * 彻底消除真机 >100 包/秒时主线程被 Binder 阻塞导致的掉帧/卡死。
     */
    private final ExecutorService parseExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "yo-ble-scan-parse");
        thread.setDaemon(true);
        return thread;
    });

    public YoBleAndroidScanPlatform(@NonNull YoBluetoothAdapterProvider adapterProvider) {
        this(adapterProvider, YoBtRadioCoordinator.forAdapterProvider(adapterProvider));
    }

    public YoBleAndroidScanPlatform(@NonNull YoBluetoothAdapterProvider adapterProvider,
                                    @NonNull YoBtRadioCoordinator radioCoordinator) {
        this.adapterProvider = Objects.requireNonNull(adapterProvider, "adapterProvider cannot be null");
        this.radioCoordinator = Objects.requireNonNull(radioCoordinator, "radioCoordinator cannot be null");
        this.radioCoordinator.registerBleScanHook(new YoBtRadioCoordinator.BleScanCoordinationHook() {
            @Override
            public void onSuspendBleScan() {
                suspendScanForRadioCoordination();
            }

            @Override
            public void onResumeBleScan() {
                resumeScanForRadioCoordination();
            }
        });
    }

    @Override
    public boolean supportsHardwareBatching() {
        BluetoothAdapter adapter = adapterProvider.adapter();
        return adapter != null && adapter.isOffloadedScanBatchingSupported();
    }

    @Override
    public boolean supportsOffloadedFiltering() {
        BluetoothAdapter adapter = adapterProvider.adapter();
        return adapter != null && adapter.isOffloadedFilteringSupported();
    }

    @Override
    public boolean supportsExtendedAdvertising() {
        // 扩展广播查询是 API 26 起才有的平台能力：低版本直接报"不支持"，不做版本分支猜测。
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return false;
        }
        BluetoothAdapter adapter = adapterProvider.adapter();
        return adapter != null && adapter.isLeExtendedAdvertisingSupported();
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

            // 申请 BLE_SCAN 租约，并协调取消经典发现
            YoBtRadioLease lease = radioCoordinator.acquireLease(
                    YoBtRadioLease.Type.BLE_SCAN, "YoBleAndroidScanPlatform");
            this.activeRadioLease = lease;
            this.activeConfig = config;
            this.activePlatformCallback = callback;
            this.scanSuspendedByRadio = false;

            final long gen = generationSeq.incrementAndGet();
            ScanSettings settings = YoBleScanCompat.toAndroidScanSettings(config);
            List<ScanFilter> filters = YoBleScanCompat.toAndroidScanFilters(config.filters());

            ScanCallback androidCallback = new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    final ScanCallback self = this;
                    if (gen != activeGeneration || activeCallback != self || result == null) {
                        return;
                    }
                    // 轻量入队：真正的 AD 解析 + Binder IPC 放在 yo-ble-scan-parse 线程，不在主线程做。
                    final ScanResult captured = result;
                    final int cbType = callbackType;
                    final long now = System.currentTimeMillis();
                    parseExecutor.execute(() -> {
                        if (gen != activeGeneration || activeCallback != self) {
                            return;
                        }
                        BleScanResult mapped;
                        try {
                            mapped = YoBleScanCompat.toBleScanResult(captured, cbType, now);
                        } catch (Throwable t) {
                            YoBtLogger.w(TAG, "convert scan result failed", t);
                            return;
                        }
                        if (gen != activeGeneration || activeCallback != self) {
                            return;
                        }
                        callback.onScanResult(mapped);
                    });
                }

                @Override
                public void onBatchScanResults(List<ScanResult> results) {
                    final ScanCallback self = this;
                    if (gen != activeGeneration || activeCallback != self
                            || results == null || results.isEmpty()) {
                        return;
                    }
                    final List<ScanResult> captured = new ArrayList<>(results);
                    final long now = System.currentTimeMillis();
                    parseExecutor.execute(() -> {
                        if (gen != activeGeneration || activeCallback != self) {
                            return;
                        }
                        List<BleScanResult> mapped = new ArrayList<>(captured.size());
                        for (ScanResult r : captured) {
                            if (r == null) {
                                continue;
                            }
                            try {
                                mapped.add(YoBleScanCompat.toBleScanResult(
                                        r, ScanSettings.CALLBACK_TYPE_ALL_MATCHES, now));
                            } catch (Throwable t) {
                                YoBtLogger.w(TAG, "convert batch scan result failed", t);
                            }
                        }
                        if (!mapped.isEmpty()
                                && gen == activeGeneration && activeCallback == self) {
                            callback.onBatchScanResults(mapped);
                        }
                    });
                }

                @Override
                public void onScanFailed(int errorCode) {
                    final ScanCallback self = this;
                    if (gen != activeGeneration || activeCallback != self) {
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
                    if (activeRadioLease != null) {
                        activeRadioLease.release();
                        activeRadioLease = null;
                    }
                    return StartResult.failed(0, "BluetoothLeScanner.startScan failed: " + fallbackErr.getMessage());
                }
            } catch (Throwable t) {
                activeCallback = null;
                activeScanner = null;
                if (activeRadioLease != null) {
                    activeRadioLease.release();
                    activeRadioLease = null;
                }
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
                } catch (Throwable t) {
                    YoBtLogger.w(TAG, "flushPendingScanResults failed", t);
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
        activeConfig = null;
        activePlatformCallback = null;
        scanSuspendedByRadio = false;

        YoBtRadioLease lease = activeRadioLease;
        activeRadioLease = null;
        if (lease != null) {
            lease.release();
        }

        if (scanner != null && cb != null) {
            try {
                scanner.stopScan(cb);
            } catch (Throwable t) {
                YoBtLogger.w(TAG, "stopScan failed", t);
            }
        }
    }

    private void suspendScanForRadioCoordination() {
        synchronized (scanLock) {
            BluetoothLeScanner scanner = activeScanner;
            ScanCallback cb = activeCallback;
            if (scanner != null && cb != null && !scanSuspendedByRadio) {
                scanSuspendedByRadio = true;
                try {
                    scanner.stopScan(cb);
                    YoBtLogger.d(TAG, "BLE scan suspended for radio arbitration (classic discovery active)");
                } catch (Throwable t) {
                    YoBtLogger.w(TAG, "suspendScan stopScan failed", t);
                }
            }
        }
    }

    private void resumeScanForRadioCoordination() {
        synchronized (scanLock) {
            if (scanSuspendedByRadio && activeCallback != null && activeConfig != null) {
                BluetoothAdapter adapter = adapterProvider.adapter();
                if (adapter != null && adapter.isEnabled()) {
                    BluetoothLeScanner scanner = adapter.getBluetoothLeScanner();
                    if (scanner != null) {
                        ScanSettings settings = YoBleScanCompat.toAndroidScanSettings(activeConfig);
                        List<ScanFilter> filters = YoBleScanCompat.toAndroidScanFilters(activeConfig.filters());
                        try {
                            if (filters.isEmpty()) {
                                scanner.startScan(null, settings, activeCallback);
                            } else {
                                scanner.startScan(filters, settings, activeCallback);
                            }
                            activeScanner = scanner;
                            scanSuspendedByRadio = false;
                            YoBtLogger.d(TAG, "BLE scan resumed after radio arbitration");
                        } catch (Throwable t) {
                            YoBtLogger.w(TAG, "resumeScan startScan failed", t);
                        }
                    }
                }
            }
        }
    }
}
