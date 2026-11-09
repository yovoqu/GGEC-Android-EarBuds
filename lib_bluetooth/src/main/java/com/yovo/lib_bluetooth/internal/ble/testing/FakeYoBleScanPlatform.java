package com.yovo.lib_bluetooth.internal.ble.testing;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.ble.scan.YoBleScanPlatform;
import com.yovo.lib_bluetooth.model.ble.BleScanConfig;
import com.yovo.lib_bluetooth.model.ble.BleScanResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * BLE 扫描平台可控 Fake 实现。
 */
public final class FakeYoBleScanPlatform implements YoBleScanPlatform {

    private int startCount = 0;
    private int stopCount = 0;
    private int flushCount = 0;
    private boolean active = false;
    private boolean flushReturnValue = true;
    @Nullable
    private String nextStartError;
    @Nullable
    private BleScanConfig lastConfig;
    @Nullable
    private PlatformCallback currentCallback;
    private final List<BleScanConfig> startHistory = new ArrayList<>();

    @Nullable
    @Override
    public synchronized String startPlatformScan(@NonNull BleScanConfig config,
                                                 @NonNull PlatformCallback callback) {
        startCount++;
        lastConfig = config;
        startHistory.add(config);
        if (nextStartError != null) {
            String err = nextStartError;
            nextStartError = null;
            return err;
        }
        active = true;
        currentCallback = callback;
        return null;
    }

    @Override
    public synchronized void stopPlatformScan() {
        stopCount++;
        active = false;
        currentCallback = null;
    }

    @Override
    public synchronized boolean flushPendingScanResults() {
        flushCount++;
        return active && flushReturnValue;
    }

    public void emitResult(@NonNull BleScanResult result) {
        PlatformCallback cb;
        synchronized (this) {
            cb = currentCallback;
        }
        if (cb != null) {
            cb.onScanResult(result);
        }
    }

    public void emitBatch(@NonNull List<BleScanResult> results) {
        PlatformCallback cb;
        synchronized (this) {
            cb = currentCallback;
        }
        if (cb != null) {
            cb.onBatchScanResults(results);
        }
    }

    public void emitError(int rawErrorCode, @Nullable String detail) {
        PlatformCallback cb;
        synchronized (this) {
            cb = currentCallback;
        }
        if (cb != null) {
            cb.onPlatformScanFailed(rawErrorCode, detail);
        }
    }

    public synchronized FakeYoBleScanPlatform setNextStartError(@Nullable String nextStartError) {
        this.nextStartError = nextStartError;
        return this;
    }

    public synchronized FakeYoBleScanPlatform setFlushReturnValue(boolean flushReturnValue) {
        this.flushReturnValue = flushReturnValue;
        return this;
    }

    public synchronized int startCount() {
        return startCount;
    }

    public synchronized int stopCount() {
        return stopCount;
    }

    public synchronized int flushCount() {
        return flushCount;
    }

    public synchronized boolean isActive() {
        return active;
    }

    @Nullable
    public synchronized BleScanConfig lastConfig() {
        return lastConfig;
    }

    @NonNull
    public synchronized List<BleScanConfig> startHistory() {
        return Collections.unmodifiableList(new ArrayList<>(startHistory));
    }
}
