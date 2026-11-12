package com.yovo.lib_bluetooth.internal.ble.scan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.ble.device.YoBleCapabilityInspector;
import com.yovo.lib_bluetooth.internal.ble.device.YoBleDeviceSnapshotStore;
import com.yovo.lib_bluetooth.internal.ble.diagnostics.YoBleTimelineRecorder;
import com.yovo.lib_bluetooth.internal.ble.error.YoBleErrorMapper;
import com.yovo.lib_bluetooth.internal.ble.error.YoBleScanAdvertiseStatus;
import com.yovo.lib_bluetooth.internal.ble.runtime.YoBleRuntime;
import com.yovo.lib_bluetooth.internal.common.concurrency.YoTimeoutHandle;
import com.yovo.lib_bluetooth.internal.common.lifecycle.YoReleasable;
import com.yovo.lib_bluetooth.model.ble.BleConfig;
import com.yovo.lib_bluetooth.model.ble.BleErrorCode;
import com.yovo.lib_bluetooth.model.ble.BleFailure;
import com.yovo.lib_bluetooth.model.ble.BleOperationHandle;
import com.yovo.lib_bluetooth.model.ble.BleScanObserver;
import com.yovo.lib_bluetooth.model.ble.BleScanResult;
import com.yovo.lib_bluetooth.model.ble.BleScanThrottlePolicy;
import com.yovo.lib_bluetooth.model.common.Result;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * BLE 扫描控制器：只负责受理请求、驱动 {@link ScanState} 状态机并编排各策略组件。
 *
 * <p>职责分工：前置校验 {@link YoBleScanPreflight}，统计 {@link YoBleScanStats}，lost 清扫定时
 * {@link YoBleScanLostSweeper}，软件批处理 flush 定时 {@link YoBleScanBatchFlusher}，长扫重启定时
 * {@link YoBleScanLongScanRestarter}，适配器恢复暂存 {@link YoBleScanAdapterRecovery}，降级重试配额
 * {@link YoBleScanDegradeRetry}，平台单线程执行器 {@link YoBleScanPlatformExecutor}。</p>
 */
public final class YoBleScanController implements YoReleasable, YoBleScanHub.Engine {

    /**
     * 显式扫描状态机（对齐 nRF Connect / RxAndroidBle 的 Stopped/Starting/Scanning/Stopping 语义）。
     * 由 state 线程驱动；{@link #state()} 为外部/诊断可观测的聚合视图。
     */
    public enum ScanState {
        IDLE,
        STARTING,
        SCANNING,
        STOPPING,
        FAILED,
        THROTTLED_WAIT
    }

    private static final String MODULE = "ble-scan";

    private final YoBleRuntime runtime;
    private final YoBleScanPlatform scanPlatform;
    private final YoBleDeviceSnapshotStore snapshotStore;
    private final YoBleTimelineRecorder timeline;
    private final YoBleScanThrottleGuard throttleGuard;
    private final YoBleScanPreflight preflight;
    private final YoBleScanStats stats = new YoBleScanStats();
    private final YoBleScanAdapterRecovery adapterRecovery = new YoBleScanAdapterRecovery();
    private final YoBleScanDegradeRetry degradeRetry = new YoBleScanDegradeRetry();
    private final YoBleScanCallbackEmulator callbackEmulator = new YoBleScanCallbackEmulator();
    private final YoBleScanSoftwareBatch softwareBatch = new YoBleScanSoftwareBatch();
    private final YoBleScanPlatformExecutor platformExecutor = new YoBleScanPlatformExecutor();
    private final YoBleScanLostSweeper lostSweeper;
    private final YoBleScanBatchFlusher batchFlusher;
    private final YoBleScanLongScanRestarter longScanRestarter;
    private final LongSupplier clockMillis;
    private final AtomicLong scanSeq = new AtomicLong();

    private long activeGeneration = 0L;
    private volatile boolean scanning = false;
    private volatile boolean pendingStart = false;
    @Nullable
    private BleScanConfig activeConfig;
    @Nullable
    private BleScanObserver activeObserver;
    @Nullable
    private YoTimeoutHandle timeoutHandle;
    @Nullable
    private YoTimeoutHandle delayedStartHandle;
    private volatile boolean released = false;
    /** scan() 已受理但对应 state 任务尚未执行完；用于消除“刚发起就停”竞态（BLE-01）。 */
    private volatile boolean startInFlight = false;
    /** 最近一次启动是否以失败告终（供 ScanState.FAILED 视图）。 */
    private volatile ScanState state = ScanState.IDLE;

    /** 唯一状态写入口：进入新状态并记录时间线。 */
    private void enterState(ScanState next) {
        if (state != next) {
            state = next;
            timeline.record(MODULE, "scan-state " + next);
        }
    }

    public YoBleScanController(@NonNull YoBleRuntime runtime,
                               @NonNull BleConfig bleConfig,
                               @NonNull YoBleCapabilityInspector capabilityInspector,
                               @NonNull YoBleScanPlatform scanPlatform,
                               @NonNull YoBleDeviceSnapshotStore snapshotStore,
                               @NonNull YoBleTimelineRecorder timeline,
                               @NonNull LongSupplier clockMillis) {
        this.runtime = runtime;
        this.scanPlatform = scanPlatform;
        this.snapshotStore = snapshotStore;
        this.timeline = timeline;
        this.clockMillis = clockMillis;
        this.preflight = new YoBleScanPreflight(capabilityInspector);
        this.throttleGuard = new YoBleScanThrottleGuard(
                bleConfig.scanThrottleWindowMillis(),
                bleConfig.scanThrottleMaxStarts(),
                clockMillis);
        this.lostSweeper = new YoBleScanLostSweeper(runtime, lostSweepHost);
        this.batchFlusher = new YoBleScanBatchFlusher(runtime, batchFlushHost);
        this.longScanRestarter = new YoBleScanLongScanRestarter(runtime, this::restartPlatformScan);
    }

    // ── YoBleScanHub.Engine：进程内扫描器使用的稳定引擎实例 ──────────

    @NonNull
    @Override
    public BleOperationHandle start(@NonNull BleScanObserver fanout) {
        return scan(YoBleScanDefaults.config(), fanout);
    }

    @NonNull
    @Override
    public Result<Void> stop() {
        return stopScan();
    }

    @NonNull
    BleOperationHandle scan(@NonNull BleScanConfig config,
                            @NonNull BleScanObserver observer) {
        long gen = scanSeq.incrementAndGet();
        String opId = "ble-scan-" + gen;
        if (released || runtime.isReleased()) {
            // 运行时已关闭：不能静默丢弃（BLE-02）。直接在调用线程回失败，保证“要么受理、要么失败回调”。
            enterState(ScanState.FAILED);
            stats.recordFail();
            observer.onScanFailed(YoBleErrorMapper.failure(
                    BleErrorCode.RELEASED, MODULE, "YoBleClient has been released"));
            return new BleOperationHandle(opId, () -> {
            });
        }
        // 受理标记：volatile，供调用线程的 stopScan() 立即识别“已受理但状态线程尚未跑完”。
        startInFlight = true;
        // 所有状态/会话字段的写入都收敛到 state 线程，消除调用线程与 state 线程的竞态（BLE-状态机）。
        runtime.state(() -> {
            enterState(ScanState.STARTING);
            adapterRecovery.clear();
            startScanInternal(gen, config, observer);
        });
        return new BleOperationHandle(opId, () -> runtime.state(() -> stopGeneration(gen, true)));
    }

    @NonNull
    public Result<Void> stopScan() {
        if (released) {
            return Result.failure(YoBleErrorMapper.failure(
                    BleErrorCode.RELEASED, MODULE, "BLE client already released"));
        }
        if (!scanning && !pendingStart && !startInFlight) {
            // 已无活动会话，但外部停止仍要放弃适配器恢复现场：适配器中断时暂存的观察者
            // 可能已被订阅者取消（如业务侧失败后立即退订），恢复它就会启动一次无人接收的扫描。
            runtime.state(adapterRecovery::clear);
            return Result.failure(YoBleErrorMapper.failure(
                    BleErrorCode.ILLEGAL_STATE, MODULE, "No BLE scan is active"));
        }
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
            return Result.failure(YoBleErrorMapper.failure(
                    BleErrorCode.ILLEGAL_STATE, MODULE, "No BLE scan is active"));
        }
        runtime.state(() -> {
            if (!scanning || released) {
                return;
            }
            platformExecutor.submit(() -> {
                scanPlatform.flushPendingScanResults();
                runtime.state(() -> {
                    if (scanning && !released) {
                        flushSoftwareBatch();
                    }
                });
            });
        });
        return Result.success(null);
    }

    public boolean isScanning() {
        return scanning;
    }

    /**
     * 聚合扫描状态（用于 UI/诊断；状态迁移仍在 state 线程内原子发生）。
     */
    @NonNull
    public ScanState state() {
        return state;
    }

    /** 扫描会话统计（启动/发现/过期/失败/停止次数与上次扫描时长）。 */
    @NonNull
    public YoBleScanStats.Stats stats() {
        return stats.snapshot();
    }

    /** 当前扫描聚合中的设备数（供诊断）。 */
    public int activeDeviceCount() {
        return callbackEmulator.activeDeviceCount();
    }

    @Override
    public void release() {
        runtime.state(() -> {
            released = true;
            adapterRecovery.clear();
            stopActiveInternal(false);
            platformExecutor.shutdown();
        });
    }

    /** 通知扫描层蓝牙适配器关闭，立即中止在途扫描。 */
    public void onAdapterTurnedOff() {
        runtime.state(() -> {
            runtime.checkStateThread();
            if (!scanning && !pendingStart && !startInFlight && activeObserver == null) {
                return;
            }
            BleScanObserver observer = activeObserver;
            // 暂存以便适配器重新开启后自动恢复（G8）。
            if (activeConfig != null && observer != null) {
                adapterRecovery.recordInterrupted(activeConfig, observer);
            }
            stopActiveInternal(false);
            enterState(ScanState.FAILED);
            if (observer != null) {
                BleFailure failure = YoBleErrorMapper.failure(
                        BleErrorCode.BLUETOOTH_DISABLED, MODULE,
                        "Bluetooth adapter turned off during scan");
                runtime.callback(() -> observer.onScanFailed(failure));
            }
        });
    }

    /** 适配器重新开启：若此前扫描被适配器关闭中断，则按原配置自动恢复（G8）。 */
    public void onAdapterTurnedOn() {
        runtime.state(() -> {
            if (released) {
                return;
            }
            YoBleScanAdapterRecovery.Pending pending = adapterRecovery.takeResumable(
                    scanning || startInFlight || pendingStart);
            if (pending == null) {
                return;
            }
            long generation = scanSeq.incrementAndGet();
            enterState(ScanState.STARTING);
            startInFlight = true;
            timeline.record(MODULE, "adapter back on, resume scan");
            startScanInternal(generation, pending.config(), pending.observer());
        });
    }

    private void startScanInternal(long generation,
                                   @NonNull BleScanConfig config,
                                   @NonNull BleScanObserver observer) {
        runtime.checkStateThread();
        if (released || runtime.isReleased()) {
            startInFlight = false;
            enterState(ScanState.FAILED);
            stats.recordFail();
            emitFailure(observer, YoBleErrorMapper.failure(
                    BleErrorCode.RELEASED, MODULE, "YoBleClient has been released"));
            return;
        }
        BleFailure preflightError = preflight.check(config, MODULE);
        if (preflightError != null) {
            startInFlight = false;
            enterState(ScanState.FAILED);
            stats.recordFail();
            timeline.record(MODULE, "preflight rejected: " + preflightError.errorCode());
            emitFailure(observer, preflightError);
            return;
        }

        long delayMs = config.throttlePolicy() == BleScanThrottlePolicy.UNRESTRICTED
                ? 0L : throttleGuard.requiredDelayMillis();
        if (delayMs > 0L && config.throttlePolicy() == BleScanThrottlePolicy.FAIL_FAST) {
            BleFailure throttled = YoBleErrorMapper.failure(
                    BleErrorCode.SCAN_THROTTLED, MODULE,
                    "BLE scan throttled (max starts within 30s reached), retry after "
                            + delayMs + "ms",
                    null, YoBleScanAdvertiseStatus.SCAN_FAILED_SCANNING_TOO_FREQUENTLY,
                    null, null, "Wait " + delayMs + "ms before calling scan()");
            timeline.record(MODULE, "scan throttled (FAIL_FAST), waitMs=" + delayMs);
            startInFlight = false;
            enterState(ScanState.FAILED);
            stats.recordFail();
            emitFailure(observer, throttled);
            return;
        }

        // 重启前收拾上一代会话：这是内部清理，不是外部停止，不动适配器恢复现场。
        stopActiveInternal(false);

        activeGeneration = generation;
        activeConfig = config;
        activeObserver = observer;
        callbackEmulator.reset();

        if (delayMs > 0L) {
            pendingStart = true;
            enterState(ScanState.THROTTLED_WAIT);
            timeline.record(MODULE, "scan delayed by throttle guard: waitMs=" + delayMs);
            delayedStartHandle = runtime.scheduler().schedule(
                    () -> runtime.state(() -> {
                        if (!pendingStart || released || activeGeneration != generation) {
                            return;
                        }
                        delayedStartHandle = null;
                        pendingStart = false;
                        launchPlatformScan(generation, config, observer);
                    }),
                    delayMs);
            startInFlight = false;
            return;
        }

        launchPlatformScan(generation, config, observer);
    }

    private void launchPlatformScan(long generation,
                                    @NonNull BleScanConfig config,
                                    @NonNull BleScanObserver observer) {
        launchPlatformScan(generation, config, observer, true);
    }

    private void launchPlatformScan(long generation,
                                    @NonNull BleScanConfig config,
                                    @NonNull BleScanObserver observer,
                                    boolean firstStart) {
        if (released || activeGeneration != generation) {
            return;
        }
        // 重启/再启动前取消上一条软件批量 flush 循环，避免叠加（BLE-04）。
        batchFlusher.stop();
        YoBleScanPlatform.PlatformCallback callback = new YoBleScanPlatform.PlatformCallback() {
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
        };
        // BLE-03：平台 binder 调用与硬件批量能力查询放到专用串行线程，结果回投 state 线程。
        platformExecutor.submit(() -> {
            boolean softwareBatchMode = config.reportDelayMillis() > 0L && !scanPlatform.supportsHardwareBatching();
            BleScanConfig platformConfig = softwareBatchMode ? config.withReportDelayMillis(0L) : config;
            YoBleScanPlatform.StartResult result = scanPlatform.startPlatformScan(platformConfig, callback);
            if (!result.success
                    && !softwareBatchMode
                    && config.reportDelayMillis() > 0L
                    && result.rawCode == YoBleScanAdvertiseStatus.SCAN_FAILED_FEATURE_UNSUPPORTED) {
                softwareBatchMode = true;
                result = scanPlatform.startPlatformScan(config.withReportDelayMillis(0L), callback);
            }
            final boolean softwareBatchResult = softwareBatchMode;
            final YoBleScanPlatform.StartResult finalResult = result;
            if (runtime.isReleased()) {
                if (finalResult.success) {
                    scanPlatform.stopPlatformScan();
                }
                return;
            }
            runtime.state(() -> onPlatformStartResult(
                    generation, config, observer, firstStart, softwareBatchResult, finalResult));
        });
    }

    /** 平台启动结果回到 state 线程后统一处理（原同步逻辑迁移；BLE-03）。 */
    private void onPlatformStartResult(long generation,
                                       @NonNull BleScanConfig config,
                                       @NonNull BleScanObserver observer,
                                       boolean firstStart,
                                       boolean softwareBatchResult,
                                       @NonNull YoBleScanPlatform.StartResult platformResult) {
        runtime.checkStateThread();
        startInFlight = false;
        if (released || activeGeneration != generation) {
            // 启动期间已被停止/切换会话：若平台已启动则立即停止，避免泄漏。
            if (platformResult.success) {
                platformExecutor.submit(scanPlatform::stopPlatformScan);
            }
            return;
        }
        if (!platformResult.success) {
            enterState(ScanState.FAILED);
            stats.recordFail();
            cancelTimerHandles();
            softwareBatch.stop();
            scanning = false;
            pendingStart = false;
            activeConfig = null;
            activeObserver = null;
            BleErrorCode code = platformResult.rawCode == 0
                    ? BleErrorCode.SCAN_START_FAILED
                    : YoBleScanAdvertiseStatus.toScanErrorCode(platformResult.rawCode);
            BleFailure failure = YoBleErrorMapper.failure(
                    code, MODULE,
                    platformResult.detail != null ? platformResult.detail : "platform startScan failed",
                    null, platformResult.rawCode, null, null, null);
            timeline.record(MODULE, "platform startScan error: " + code + " " + platformResult.detail);
            emitFailure(observer, failure);
            return;
        }

        if (softwareBatchResult) {
            softwareBatch.start();
        } else {
            softwareBatch.stop();
        }
        if (firstStart) {
            throttleGuard.recordScanStarted();
        }
        enterState(ScanState.SCANNING);
        scanning = true;
        pendingStart = false;
        stats.recordStart();
        timeline.record(MODULE, "scan started gen=" + generation
                + ", filters=" + config.filters().size()
                + ", timeoutMs=" + config.timeoutMillis()
                + ", softwareBatch=" + softwareBatch.isActive()
                + ", restart=" + !firstStart);
        if (firstStart) {
            runtime.callback(observer::onScanStarted);
            if (config.timeoutMillis() > 0L) {
                timeoutHandle = runtime.scheduler().schedule(
                        () -> runtime.state(() -> stopGeneration(generation, true)),
                        config.timeoutMillis());
            }
            lostSweeper.arm(generation);
        }
        longScanRestarter.arm(generation, config.timeoutMillis());
        if (softwareBatch.isActive()) {
            batchFlusher.start(generation);
        }
    }

    /** 长扫重启：先停掉底层扫描，再按同一代配置重新启动。 */
    private void restartPlatformScan(long generation) {
        runtime.checkStateThread();
        if (!scanning || released || activeGeneration != generation
                || activeConfig == null || activeObserver == null) {
            return;
        }
        BleScanConfig config = activeConfig;
        BleScanObserver observer = activeObserver;
        platformExecutor.submit(scanPlatform::stopPlatformScan);
        launchPlatformScan(generation, config, observer, false);
    }

    private void flushSoftwareBatch() {
        if (softwareBatch.isEmpty() || activeObserver == null) {
            return;
        }
        List<BleScanResult> copy = softwareBatch.drain();
        BleScanObserver observer = activeObserver;
        runtime.callback(() -> observer.onBatchScanResults(copy));
    }

    private void handlePlatformResult(long generation, @NonNull BleScanResult result) {
        runtime.checkStateThread();
        if (!scanning || released || activeGeneration != generation || activeConfig == null) {
            return;
        }
        if (!YoBleScanFilterMatcher.matchesConfig(activeConfig, result)) {
            return;
        }
        snapshotStore.recordScanResult(result);
        List<BleScanResult> emitted = callbackEmulator.onResult(
                activeConfig, result, clockMillis.getAsLong());
        stats.recordFounds(emitted.size());
        lostSweeper.arm(generation);
        if (softwareBatch.isActive()) {
            softwareBatch.add(emitted);
            return;
        }
        BleScanObserver observer = activeObserver;
        if (observer != null && !emitted.isEmpty()) {
            for (BleScanResult item : emitted) {
                runtime.callback(() -> observer.onDeviceFound(item));
            }
        }
    }

    private void handlePlatformBatch(long generation, @NonNull List<BleScanResult> results) {
        runtime.checkStateThread();
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
        stats.recordFounds(filtered.size());
        lostSweeper.arm(generation);
        if (softwareBatch.isActive()) {
            softwareBatch.add(filtered);
            return;
        }
        BleScanObserver observer = activeObserver;
        if (observer != null && !filtered.isEmpty()) {
            runtime.callback(() -> observer.onBatchScanResults(filtered));
        }
    }

    private void handlePlatformFailure(long generation, int rawErrorCode, @Nullable String detail) {
        runtime.checkStateThread();
        if (released || !scanning || activeGeneration != generation) {
            return;
        }
        // 对齐 Nordic ScannerCompat：底层报 FEATURE_UNSUPPORTED / OUT_OF_HARDWARE_RESOURCES 时，
        // 先降级为“无硬件过滤 + reportDelay=0”的软件模式重试一次，而不是直接失败。每代仅一次。
        BleScanConfig currentConfig = activeConfig;
        BleScanObserver currentObserver = activeObserver;
        if (currentConfig != null && currentObserver != null
                && degradeRetry.tryDegrade(generation, rawErrorCode)) {
            timeline.record(MODULE, "platform scan failed raw=" + rawErrorCode
                    + ", degrade to software (no hw filters/batch) & retry once");
            platformExecutor.submit(scanPlatform::stopPlatformScan);
            launchPlatformScan(generation, currentConfig.softwareFallback(), currentObserver, false);
            return;
        }
        stats.recordFail();
        BleScanObserver observer = activeObserver;
        stopActiveInternal(false);
        enterState(ScanState.FAILED);
        BleErrorCode code = YoBleScanAdvertiseStatus.toScanErrorCode(rawErrorCode);
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

    private void cancelTimerHandles() {
        if (timeoutHandle != null) {
            timeoutHandle.cancel();
            timeoutHandle = null;
        }
        if (delayedStartHandle != null) {
            delayedStartHandle.cancel();
            delayedStartHandle = null;
        }
        lostSweeper.disarm();
        batchFlusher.stop();
        longScanRestarter.disarm();
    }

    private void stopActiveInternal(boolean notifyStopped) {
        runtime.checkStateThread();
        if (notifyStopped) {
            // 外部主动停止（订阅者取消/用户停止）即放弃适配器自动恢复：否则适配器重新开启时
            // 会用已经退订的观察者重启一次无人接收的扫描，且它没有产品超时。
            adapterRecovery.clear();
        }
        boolean wasScanning = scanning;
        // 是否存在需要收尾的“实际扫描会话”：STARTING / THROTTLED_WAIT / SCANNING 三种情况。
        boolean hadSession = wasScanning || pendingStart || activeObserver != null;
        if (!hadSession) {
            // 无实际扫描可停（如启动前的内部复位）：不改变状态，避免 STARTING↔IDLE 抖动。
            return;
        }
        if (wasScanning) {
            enterState(ScanState.STOPPING);
        }
        cancelTimerHandles();
        long stoppedGeneration = activeGeneration;
        pendingStart = false;
        scanning = false;
        startInFlight = false;
        // 使任何已入队/在途的平台启动结果失效（onPlatformStartResult 见 generation 不匹配会自行停平台）。
        activeGeneration = -1L;
        BleScanObserver observer = activeObserver;
        if (wasScanning) {
            flushSoftwareBatch();
            stats.recordStop();
        }
        softwareBatch.stop();
        activeConfig = null;
        activeObserver = null;
        callbackEmulator.reset();
        // BLE-03：平台停扫放到串行平台线程；与已排队的启动任务保持 FIFO，避免“先停后启”乱序。
        platformExecutor.submit(scanPlatform::stopPlatformScan);
        timeline.record(MODULE, "scan stopped gen=" + stoppedGeneration);
        // 无论停止发生在 STARTING / THROTTLED_WAIT / SCANNING，都必须回到 IDLE 并按需回调 onScanStopped；
        // 否则状态会永久卡在 STARTING/THROTTLED_WAIT，调用方也永远等不到终态。
        enterState(ScanState.IDLE);
        if (notifyStopped && observer != null) {
            runtime.callback(observer::onScanStopped);
        }
    }

    private void emitFailure(@NonNull BleScanObserver observer, @NonNull BleFailure failure) {
        runtime.callback(() -> observer.onScanFailed(failure));
    }

    /** 该代扫描是否仍在进行：定时器据此丢弃已失效代际的回调。 */
    private boolean isScanSessionAlive(long generation) {
        return scanning && !released && activeGeneration == generation && activeConfig != null;
    }

    private final YoBleScanLostSweeper.Host lostSweepHost = new YoBleScanLostSweeper.Host() {
        @Override
        public boolean isSessionAlive(long generation) {
            return isScanSessionAlive(generation);
        }

        @Override
        public boolean isLostTrackingEnabled() {
            BleScanConfig config = activeConfig;
            return config != null && YoBleScanLostPolicy.softwareLostEnabled(config);
        }

        @Override
        public int activeDeviceCount() {
            return callbackEmulator.activeDeviceCount();
        }

        @NonNull
        @Override
        public List<BleScanResult> sweepExpiredDevices() {
            BleScanConfig config = activeConfig;
            if (config == null) {
                return new ArrayList<>(0);
            }
            return callbackEmulator.sweepLost(config, clockMillis.getAsLong());
        }

        @Override
        public void dispatchLostResults(@NonNull List<BleScanResult> lost) {
            BleScanObserver observer = activeObserver;
            if (observer == null || lost.isEmpty()) {
                return;
            }
            stats.recordLosts(lost.size());
            for (BleScanResult item : lost) {
                runtime.callback(() -> observer.onScanResultLost(item));
            }
        }
    };

    private final YoBleScanBatchFlusher.Host batchFlushHost = new YoBleScanBatchFlusher.Host() {
        @Override
        public boolean isSessionAlive(long generation) {
            return isScanSessionAlive(generation);
        }

        @Override
        public boolean isSoftwareBatchActive() {
            return softwareBatch.isActive();
        }

        @Override
        public long flushIntervalMillis() {
            // 调用前提由 YoBleScanBatchFlusher.start() 建立：它只在 isSoftwareBatchActive() 且
            // isSessionAlive(generation)（含 activeConfig != null）成立时才取间隔；软件批量只在
            // reportDelayMillis > 0 时被激活（onPlatformStartResult 的 softwareBatchResult）。
            // 判空与取值同在 state 线程，中间不存在清除 activeConfig 的写入，因此配置非空、
            // 间隔必为正——不需要 null 兜底分支，也不需要 Math.max 钳位。
            return activeConfig.reportDelayMillis();
        }

        @Override
        public void flushBufferedResults() {
            flushSoftwareBatch();
        }
    };
}
