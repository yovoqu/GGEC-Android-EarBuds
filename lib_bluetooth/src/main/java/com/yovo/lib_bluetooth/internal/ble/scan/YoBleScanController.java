package com.yovo.lib_bluetooth.internal.ble.scan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.ble.device.YoBleCapabilityInspector;
import com.yovo.lib_bluetooth.internal.ble.device.YoBleDeviceSnapshotStore;
import com.yovo.lib_bluetooth.internal.ble.diagnostics.YoBleTimelineRecorder;
import com.yovo.lib_bluetooth.internal.ble.error.YoBleErrorMapper;
import com.yovo.lib_bluetooth.internal.ble.error.YoBleGattStatus;
import com.yovo.lib_bluetooth.internal.ble.runtime.YoBleRuntime;
import com.yovo.lib_bluetooth.internal.common.concurrency.YoTimeoutHandle;
import com.yovo.lib_bluetooth.internal.common.lifecycle.YoReleasable;
import com.yovo.lib_bluetooth.model.ble.BleConfig;
import com.yovo.lib_bluetooth.model.ble.BleErrorCode;
import com.yovo.lib_bluetooth.model.ble.BleFailure;
import com.yovo.lib_bluetooth.model.ble.BleOperationHandle;
import com.yovo.lib_bluetooth.model.ble.BleScanCallbackType;
import com.yovo.lib_bluetooth.model.ble.BleScanConfig;
import com.yovo.lib_bluetooth.model.ble.BleScanObserver;
import com.yovo.lib_bluetooth.model.ble.BleScanResult;
import com.yovo.lib_bluetooth.model.ble.BleScanThrottlePolicy;
import com.yovo.lib_bluetooth.model.common.Result;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * BLE 扫描控制器：统一负责 preflight 校验（硬件/BT开关/权限/位置服务）、
 * 30s/5次防限流滑窗调度、超 时与 MatchLost 定时清理、软过滤兜底与原子停扫。
 */
public final class YoBleScanController implements YoReleasable {

    private static final String MODULE = "ble-scan";
    private static final long LOST_SWEEP_INTERVAL_MS = 1_000L;

    private final YoBleRuntime runtime;
    private final YoBleCapabilityInspector capabilityInspector;
    private final YoBleScanPlatform scanPlatform;
    private final YoBleDeviceSnapshotStore snapshotStore;
    private final YoBleTimelineRecorder timeline;
    private final YoBleScanThrottleGuard throttleGuard;
    private final YoBleScanCallbackEmulator callbackEmulator = new YoBleScanCallbackEmulator();
    private final LongSupplier clockMillis;
    private final AtomicLong scanSeq = new AtomicLong();

    private long activeGeneration = 0L;
    private volatile boolean scanning = false;
    @Nullable
    private BleScanConfig activeConfig;
    @Nullable
    private BleScanObserver activeObserver;
    @Nullable
    private YoTimeoutHandle timeoutHandle;
    @Nullable
    private YoTimeoutHandle delayedStartHandle;
    @Nullable
    private YoTimeoutHandle lostSweepHandle;
    private volatile boolean released = false;

    public YoBleScanController(@NonNull YoBleRuntime runtime,
                                 @NonNull BleConfig bleConfig,
                                 @NonNull YoBleCapabilityInspector capabilityInspector,
                                 @NonNull YoBleScanPlatform scanPlatform,
                                 @NonNull YoBleDeviceSnapshotStore snapshotStore,
                                 @NonNull YoBleTimelineRecorder timeline,
                                 @NonNull LongSupplier clockMillis) {
        this.runtime = runtime;
        this.capabilityInspector = capabilityInspector;
        this.scanPlatform = scanPlatform;
        this.snapshotStore = snapshotStore;
        this.timeline = timeline;
        this.clockMillis = clockMillis;
        this.throttleGuard = new YoBleScanThrottleGuard(
                bleConfig.scanThrottleWindowMillis(),
                bleConfig.scanThrottleMaxStarts(),
                clockMillis);
    }

    @NonNull
    public BleOperationHandle scan(@NonNull BleScanConfig config,
                                   @NonNull BleScanObserver observer) {
        long gen = scanSeq.incrementAndGet();
        String opId = "ble-scan-" + gen;
        runtime.state(() -> startScanInternal(gen, config, observer));
        return new BleOperationHandle(opId, () -> runtime.state(() -> stopGeneration(gen, true)));
    }

    @NonNull
    public Result<Void> stopScan() {
        runtime.state(() -> stopActiveInternal(true));
        return Result.success(null);
    }

    @NonNull
    public Result<Void> flushBatchScanResults() {
        if (released) {
            return Result.failure(YoBleErrorMapper.failure(
                    BleErrorCode.RELEASED, MODULE, "BLE client already released"));
        }
        if (!scanning) {
            return Result.success(null);
        }
        scanPlatform.flushPendingScanResults();
        return Result.success(null);
    }

    public boolean isScanning() {
        return scanning;
    }

    @Override
    public void release() {
        runtime.state(() -> {
            released = true;
            stopActiveInternal(false);
        });
    }

    /** 通知扫描层蓝牙适配器关闭，立即中止在途扫描。 */
    public void onAdapterTurnedOff() {
        runtime.state(() -> {
            if (!scanning) {
                return;
            }
            BleScanObserver observer = activeObserver;
            stopActiveInternal(false);
            if (observer != null) {
                BleFailure failure = YoBleErrorMapper.failure(
                        BleErrorCode.BLUETOOTH_DISABLED, MODULE,
                        "Bluetooth adapter turned off during scan");
                runtime.callback(() -> observer.onScanFailed(failure));
            }
        });
    }

    private void startScanInternal(long generation,
                                   @NonNull BleScanConfig config,
                                   @NonNull BleScanObserver observer) {
        if (released || runtime.isReleased()) {
            emitFailure(observer, YoBleErrorMapper.failure(
                    BleErrorCode.RELEASED, MODULE, "YoBleClient has been released"));
            return;
        }
        BleFailure preflightError = checkPreflight(config);
        if (preflightError != null) {
            timeline.record(MODULE, "preflight rejected: " + preflightError.errorCode());
            emitFailure(observer, preflightError);
            return;
        }

        // 先停止旧扫描或待启动计划（含取消超时定时 handle）
        stopActiveInternal(activeObserver != null);

        activeGeneration = generation;
        activeConfig = config;
        activeObserver = observer;
        callbackEmulator.reset();

        long delayMs = config.throttlePolicy() == BleScanThrottlePolicy.UNRESTRICTED
                ? 0L : throttleGuard.requiredDelayMillis();
        if (delayMs > 0L) {
            if (config.throttlePolicy() == BleScanThrottlePolicy.FAIL_FAST) {
                activeConfig = null;
                activeObserver = null;
                BleFailure throttled = YoBleErrorMapper.failure(
                        BleErrorCode.SCAN_THROTTLED, MODULE,
                        "BLE scan throttled (max starts within 30s reached), retry after "
                                + delayMs + "ms",
                        null, YoBleGattStatus.SCAN_FAILED_SCANNING_TOO_FREQUENTLY,
                        null, null, "Wait " + delayMs + "ms before calling scan()");
                timeline.record(MODULE, "scan throttled (FAIL_FAST), waitMs=" + delayMs);
                emitFailure(observer, throttled);
                return;
            }
            // DELAY_UNTIL_WINDOW_FREE：延迟等待滑窗空出再发起真实扫描
            scanning = true;
            timeline.record(MODULE, "scan delayed by throttle guard: waitMs=" + delayMs);
            delayedStartHandle = runtime.scheduler().schedule(
                    () -> runtime.state(() -> {
                        if (!scanning || released || activeGeneration != generation) {
                            return;
                        }
                        delayedStartHandle = null;
                        launchPlatformScan(generation, config, observer);
                    }),
                    delayMs);
            return;
        }

        launchPlatformScan(generation, config, observer);
    }

    private void launchPlatformScan(long generation,
                                    @NonNull BleScanConfig config,
                                    @NonNull BleScanObserver observer) {
        if (released || activeGeneration != generation) {
            return;
        }
        throttleGuard.recordScanStarted();
        String platformError = scanPlatform.startPlatformScan(config,
                new YoBleScanPlatform.PlatformCallback() {
                    @Override
                    public void onScanResult(@NonNull BleScanResult result) {
                        runtime.state(() -> handlePlatformResult(generation, result));
                    }

                    @Override
                    public void onBatchScanResults(@NonNull List<BleScanResult> results) {
                        runtime.state(() -> handlePlatformBatch(generation, results));
                    }

                    @Override
                    public void onPlatformScanFailed(int rawErrorCode, @Nullable String detail) {
                        runtime.state(() -> handlePlatformFailure(generation, rawErrorCode, detail));
                    }
                });

        if (platformError != null) {
            scanning = false;
            activeConfig = null;
            activeObserver = null;
            BleFailure failure = YoBleErrorMapper.failure(
                    BleErrorCode.SCAN_START_FAILED, MODULE, platformError);
            timeline.record(MODULE, "platform startScan error: " + platformError);
            emitFailure(observer, failure);
            return;
        }

        scanning = true;
        timeline.record(MODULE, "scan started gen=" + generation
                + ", filters=" + config.filters().size()
                + ", timeoutMs=" + config.timeoutMillis());
        runtime.callback(observer::onScanStarted);

        if (config.timeoutMillis() > 0L) {
            timeoutHandle = runtime.scheduler().schedule(
                    () -> runtime.state(() -> stopGeneration(generation, true)),
                    config.timeoutMillis());
        }
        if (config.callbackType() == BleScanCallbackType.MATCH_LOST
                || config.callbackType() == BleScanCallbackType.FIRST_MATCH_AND_LOST) {
            scheduleLostSweep(generation);
        }
    }

    private void scheduleLostSweep(long generation) {
        if (!scanning || released || activeGeneration != generation || activeConfig == null) {
            return;
        }
        lostSweepHandle = runtime.scheduler().schedule(() -> runtime.state(() -> {
            if (!scanning || released || activeGeneration != generation || activeConfig == null) {
                return;
            }
            List<BleScanResult> lost = callbackEmulator.sweepLost(activeConfig, clockMillis.getAsLong());
            BleScanObserver observer = activeObserver;
            if (observer != null && !lost.isEmpty()) {
                for (BleScanResult item : lost) {
                    runtime.callback(() -> observer.onScanResultLost(item));
                }
            }
            scheduleLostSweep(generation);
        }), LOST_SWEEP_INTERVAL_MS);
    }

    private void handlePlatformResult(long generation, @NonNull BleScanResult result) {
        if (!scanning || released || activeGeneration != generation || activeConfig == null) {
            return;
        }
        if (!YoBleScanFilterMatcher.matchesConfig(activeConfig, result)) {
            return;
        }
        snapshotStore.recordScanResult(result);
        List<BleScanResult> emitted = callbackEmulator.onResult(
                activeConfig, result, clockMillis.getAsLong());
        BleScanObserver observer = activeObserver;
        if (observer != null && !emitted.isEmpty()) {
            for (BleScanResult item : emitted) {
                runtime.callback(() -> observer.onDeviceFound(item));
            }
        }
    }

    private void handlePlatformBatch(long generation, @NonNull List<BleScanResult> results) {
        if (!scanning || released || activeGeneration != generation || activeConfig == null) {
            return;
        }
        List<BleScanResult> filtered = new ArrayList<>();
        long now = clockMillis.getAsLong();
        for (BleScanResult r : results) {
            if (!YoBleScanFilterMatcher.matchesConfig(activeConfig, r)) {
                continue;
            }
            snapshotStore.recordScanResult(r);
            filtered.addAll(callbackEmulator.onResult(activeConfig, r, now));
        }
        BleScanObserver observer = activeObserver;
        if (observer != null && !filtered.isEmpty()) {
            runtime.callback(() -> observer.onBatchScanResults(filtered));
        }
    }

    private void handlePlatformFailure(long generation, int rawErrorCode, @Nullable String detail) {
        if (!scanning || activeGeneration != generation) {
            return;
        }
        BleScanObserver observer = activeObserver;
        stopActiveInternal(false);
        BleErrorCode code = YoBleGattStatus.toScanErrorCode(rawErrorCode);
        BleFailure failure = YoBleErrorMapper.failure(
                code, MODULE,
                detail != null ? detail : ("Platform BLE scan failed: " + rawErrorCode),
                null, rawErrorCode, null, null, null);
        timeline.record(MODULE, "platform scan failed: code=" + code + ", raw=" + rawErrorCode);
        if (observer != null) {
            emitFailure(observer, failure);
        }
    }

    private void stopGeneration(long generation, boolean notifyStopped) {
        if (activeGeneration != generation) {
            return;
        }
        stopActiveInternal(notifyStopped);
    }

    private void stopActiveInternal(boolean notifyStopped) {
        if (timeoutHandle != null) {
            timeoutHandle.cancel();
            timeoutHandle = null;
        }
        if (delayedStartHandle != null) {
            delayedStartHandle.cancel();
            delayedStartHandle = null;
        }
        if (lostSweepHandle != null) {
            lostSweepHandle.cancel();
            lostSweepHandle = null;
        }
        if (!scanning) {
            return;
        }
        scanning = false;
        BleScanObserver observer = activeObserver;
        activeConfig = null;
        activeObserver = null;
        callbackEmulator.reset();
        try {
            scanPlatform.stopPlatformScan();
        } catch (Throwable ignored) {
        }
        timeline.record(MODULE, "scan stopped gen=" + activeGeneration);
        if (notifyStopped && observer != null) {
            runtime.callback(observer::onScanStopped);
        }
    }

    @Nullable
    private BleFailure checkPreflight(@NonNull BleScanConfig config) {
        if (!capabilityInspector.isBleSupported()) {
            return YoBleErrorMapper.failure(
                    BleErrorCode.BLE_NOT_SUPPORTED, MODULE, "Device does not support BLE hardware");
        }
        if (!capabilityInspector.isAdapterEnabled()) {
            return YoBleErrorMapper.failure(
                    BleErrorCode.BLUETOOTH_DISABLED, MODULE, "Bluetooth adapter is disabled");
        }
        if (!capabilityInspector.hasScanPermission()) {
            return YoBleErrorMapper.failure(
                    BleErrorCode.PERMISSION_SCAN_DENIED, MODULE, "Missing BLE scan permission");
        }
        // Android < 12 (API < 31) 或未声明位置豁免时需检查位置权限与系统位置服务开关
        boolean requiresLocation = capabilityInspector.sdkInt() < 31 || !config.locationExempt();
        if (requiresLocation) {
            if (!capabilityInspector.hasLocationPermission()) {
                return YoBleErrorMapper.failure(
                        BleErrorCode.PERMISSION_LOCATION_DENIED, MODULE,
                        "Location permission required for BLE scan on Android < 12");
            }
            if (!capabilityInspector.isLocationServicesEnabled()) {
                return YoBleErrorMapper.failure(
                        BleErrorCode.LOCATION_SERVICES_DISABLED, MODULE,
                        "System Location services must be enabled for BLE scan on Android < 12");
            }
        }
        return null;
    }

    private void emitFailure(@NonNull BleScanObserver observer, @NonNull BleFailure failure) {
        runtime.callback(() -> observer.onScanFailed(failure));
    }
}
