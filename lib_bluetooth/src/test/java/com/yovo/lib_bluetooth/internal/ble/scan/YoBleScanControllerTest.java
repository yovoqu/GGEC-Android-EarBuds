package com.yovo.lib_bluetooth.internal.ble.scan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.ble.device.YoBleCapabilityInspector;
import com.yovo.lib_bluetooth.internal.ble.device.YoBleDeviceSnapshotStore;
import com.yovo.lib_bluetooth.internal.ble.diagnostics.YoBleTimelineRecorder;
import com.yovo.lib_bluetooth.internal.ble.error.YoBleScanAdvertiseStatus;
import com.yovo.lib_bluetooth.internal.ble.runtime.YoBleRuntime;
import com.yovo.lib_bluetooth.internal.ble.runtime.YoBleRuntimeConfig;
import com.yovo.lib_bluetooth.model.ble.BleAdapterState;
import com.yovo.lib_bluetooth.model.ble.BleConfig;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleDeviceIdentity;
import com.yovo.lib_bluetooth.model.ble.BleFailure;
import com.yovo.lib_bluetooth.model.ble.BleOperationHandle;
import com.yovo.lib_bluetooth.model.ble.BleScanObserver;
import com.yovo.lib_bluetooth.model.ble.BleScanResult;

import org.junit.After;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * YoBleScanController 生命周期回归：扫描启动/停止竞态、运行时关闭回失败、平台启动失败复位。
 * 覆盖审计 BLE-01 / BLE-02 / BLE-05。
 */
public final class YoBleScanControllerTest {

    @After
    public void resetHub() {
        YoBleScanHub.resetForTest();
    }

    @Test
    public void stopImmediatelyAfterScanDoesNotLeaveScanningLatch() throws Exception {
        FakePlatform platform = new FakePlatform();
        YoBleRuntime runtime = newRuntime();
        YoBleScanController controller = newController(runtime, platform);

        controller.scan(quickConfig(), new BleScanObserver() {
        });
        // 关键：启动任务可能尚未执行，此时立刻停扫。修复前会返回 ILLEGAL_STATE 且不停，扫描续跑。
        controller.stopScan();

        await(() -> !controller.isScanning() && platform.stopCount.get() >= 1, 3000);
        assertFalse("扫描应在“刚发起就停”后回落非扫描态", controller.isScanning());
        assertFalse("平台扫描应已停止", platform.started);
        assertEquals("停止后状态机必须回到 IDLE（不得卡在 STARTING）",
                YoBleScanController.ScanState.IDLE, controller.state());
        runtime.release();
    }

    @Test
    public void scanAfterRuntimeReleasedDeliversFailureInsteadOfSilentDrop() throws Exception {
        FakePlatform platform = new FakePlatform();
        YoBleRuntime runtime = newRuntime();
        YoBleScanController controller = newController(runtime, platform);

        runtime.release();
        await(runtime::isReleased, 2000);

        AtomicReference<BleFailure> failure = new AtomicReference<>();
        AtomicInteger startedCount = new AtomicInteger();
        controller.scan(quickConfig(), new BleScanObserver() {
            @Override
            public void onScanStarted() {
                startedCount.incrementAndGet();
            }

            @Override
            public void onScanFailed(@NonNull BleFailure f) {
                failure.set(f);
            }
        });

        assertNotNull("运行时已释放时必须回调失败，而非静默丢弃", failure.get());
        assertTrue("不得启动平台扫描", startedCount.get() == 0 && platform.startCount.get() == 0);
    }

    @Test
    public void platformStartFailureResetsScanningState() throws Exception {
        FakePlatform platform = new FakePlatform();
        platform.nextStart = YoBleScanPlatform.StartResult.failed(
                YoBleScanAdvertiseStatus.SCAN_FAILED_INTERNAL_ERROR, "boom");
        YoBleRuntime runtime = newRuntime();
        YoBleScanController controller = newController(runtime, platform);

        AtomicReference<BleFailure> failure = new AtomicReference<>();
        controller.scan(quickConfig(), new BleScanObserver() {
            @Override
            public void onScanFailed(@NonNull BleFailure f) {
                failure.set(f);
            }
        });

        await(() -> failure.get() != null, 3000);
        assertNotNull(failure.get());
        assertFalse("平台启动失败后必须复位 scanning，避免闩锁", controller.isScanning());
        runtime.release();
    }

    @Test
    public void platformStartRunsOnDedicatedThreadNotStateThread() throws Exception {
        FakePlatform platform = new FakePlatform();
        YoBleRuntime runtime = newRuntime();
        YoBleScanController controller = newController(runtime, platform);

        controller.scan(quickConfig(), new BleScanObserver() {
        });
        await(() -> platform.startCount.get() >= 1, 3000);

        assertNotNull(platform.startThreadName);
        assertEquals("yo-ble-scan-platform", platform.startThreadName);
        await(() -> controller.isScanning(), 2000);

        controller.stopScan();
        await(() -> !controller.isScanning(), 2000);
        runtime.release();
    }

    @Test
    public void stateMachineReportsScanningThenIdleAfterStop() throws Exception {
        FakePlatform platform = new FakePlatform();
        YoBleRuntime runtime = newRuntime();
        YoBleScanController controller = newController(runtime, platform);

        assertEquals(YoBleScanController.ScanState.IDLE, controller.state());
        controller.scan(quickConfig(), new BleScanObserver() {
        });
        await(() -> controller.state() == YoBleScanController.ScanState.SCANNING, 3000);
        assertEquals(YoBleScanController.ScanState.SCANNING, controller.state());

        controller.stopScan();
        await(() -> controller.state() == YoBleScanController.ScanState.IDLE, 2000);
        assertEquals(YoBleScanController.ScanState.IDLE, controller.state());
        runtime.release();
    }

    @Test
    public void stateMachineReportsFailedOnPlatformStartFailure() throws Exception {
        FakePlatform platform = new FakePlatform();
        platform.nextStart = YoBleScanPlatform.StartResult.failed(
                YoBleScanAdvertiseStatus.SCAN_FAILED_INTERNAL_ERROR, "boom");
        YoBleRuntime runtime = newRuntime();
        YoBleScanController controller = newController(runtime, platform);

        controller.scan(quickConfig(), new BleScanObserver() {
        });
        await(() -> controller.state() == YoBleScanController.ScanState.FAILED, 3000);
        assertEquals(YoBleScanController.ScanState.FAILED, controller.state());
        runtime.release();
    }

    @Test
    public void resumesScanAfterAdapterTurnsBackOn() throws Exception {
        FakePlatform platform = new FakePlatform();
        YoBleRuntime runtime = newRuntime();
        YoBleScanController controller = newController(runtime, platform);

        controller.scan(quickConfig(), new BleScanObserver() {
        });
        await(() -> controller.state() == YoBleScanController.ScanState.SCANNING, 3000);
        int startsBeforeOff = platform.startCount.get();

        controller.onAdapterTurnedOff();
        await(() -> controller.state() != YoBleScanController.ScanState.SCANNING, 2000);

        controller.onAdapterTurnedOn();
        await(() -> controller.state() == YoBleScanController.ScanState.SCANNING
                && platform.startCount.get() > startsBeforeOff, 3000);
        assertEquals(YoBleScanController.ScanState.SCANNING, controller.state());
        runtime.release();
    }

    /**
     * 经 {@link YoBleScanHub} 的真实投递路径验证「失败不销毁会话」：适配器关闭 → 订阅者收到
     * {@code onScanFailed} 且仍是订阅者 → 适配器开启 → 恢复后同一订阅者继续收到帧。
     */
    @Test
    public void adapterCycleThroughHubKeepsSubscriberAndResumesFrames() throws Exception {
        FakePlatform platform = new FakePlatform();
        YoBleRuntime runtime = newRuntime();
        YoBleScanController controller = newController(runtime, platform);

        AtomicInteger started = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        AtomicInteger founds = new AtomicInteger();
        BleOperationHandle subscription = YoBleScanHub.subscribe(controller, new BleScanObserver() {
            @Override
            public void onScanStarted() {
                started.incrementAndGet();
            }

            @Override
            public void onScanFailed(@NonNull BleFailure failure) {
                failures.incrementAndGet();
            }

            @Override
            public void onDeviceFound(@NonNull BleScanResult result) {
                founds.incrementAndGet();
            }
        });

        await(() -> controller.state() == YoBleScanController.ScanState.SCANNING, 3000);
        // onScanStarted 由 state 线程投递到回调线程（callbackOnMainThread=false），
        // 与 SCANNING 的可见性不同步：必须等回调真正送达，不能只看状态。
        await(() -> started.get() == 1, 3000);
        assertEquals("Hub 订阅必须真正启动引擎", 1, started.get());
        platform.callback.onScanResult(scanResult());
        await(() -> founds.get() >= 1, 3000);

        controller.onAdapterTurnedOff();
        await(() -> failures.get() == 1, 3000);
        assertEquals("适配器关闭必须以失败送达同一订阅者", 1, failures.get());
        assertFalse("关闭期间不得伪造扫描中状态", controller.isScanning());

        int startsBeforeResume = platform.startCount.get();
        int foundsBeforeResume = founds.get();
        controller.onAdapterTurnedOn();
        await(() -> platform.startCount.get() > startsBeforeResume
                && controller.state() == YoBleScanController.ScanState.SCANNING, 3000);

        platform.callback.onScanResult(scanResult());
        await(() -> founds.get() > foundsBeforeResume, 3000);
        assertTrue("恢复后同一订阅者必须继续收到帧", founds.get() > foundsBeforeResume);

        subscription.cancel();
        await(() -> !controller.isScanning(), 3000);
        runtime.release();
    }

    /**
     * 适配器关闭后订阅者退订（外部停止）→ 适配器重新开启时**不得**恢复扫描：
     * 中断现场里的观察者已经退订，恢复它只会启动一次无人接收、也没有产品超时的扫描。
     */
    @Test
    public void doesNotResumeAfterSubscriberStopsDuringAdapterOff() throws Exception {
        FakePlatform platform = new FakePlatform();
        YoBleRuntime runtime = newRuntime();
        YoBleScanController controller = newController(runtime, platform);

        AtomicInteger failures = new AtomicInteger();
        BleOperationHandle subscription = YoBleScanHub.subscribe(controller, new BleScanObserver() {
            @Override
            public void onScanFailed(@NonNull BleFailure failure) {
                failures.incrementAndGet();
            }
        });
        await(() -> controller.state() == YoBleScanController.ScanState.SCANNING, 3000);

        controller.onAdapterTurnedOff();
        await(() -> failures.get() == 1, 3000);
        int startsBeforeResume = platform.startCount.get();

        // 订阅者退订：Hub 最后一个订阅者离开会停引擎，控制器据此放弃恢复现场。
        // 停扫由 Hub 的会话迁移线程推进（不在句柄取消栈上调用引擎），等它落地再驱动适配器。
        subscription.cancel();
        CountDownLatch transitionBarrier = YoBleScanHub.submitTransitionBarrierForTest();
        assertTrue("Hub 会话迁移必须在超时内排空",
                transitionBarrier.await(5L, TimeUnit.SECONDS));
        await(() -> !controller.isScanning(), 3000);

        controller.onAdapterTurnedOn();
        await(() -> !controller.isScanning() && platform.startCount.get() == startsBeforeResume, 1500);
        assertFalse("退订后适配器恢复不得重启扫描", controller.isScanning());
        assertEquals("不得为已退订的观察者启动平台扫描",
                startsBeforeResume, platform.startCount.get());
        runtime.release();
    }

    @Test
    public void degradesAndRetriesOnceWhenPlatformReportsFeatureUnsupported() throws Exception {
        FakePlatform platform = new FakePlatform();
        YoBleRuntime runtime = newRuntime();
        YoBleScanController controller = newController(runtime, platform);

        AtomicInteger failures = new AtomicInteger();
        controller.scan(quickConfig(), new BleScanObserver() {
            @Override
            public void onScanFailed(@NonNull com.yovo.lib_bluetooth.model.ble.BleFailure f) {
                failures.incrementAndGet();
            }
        });
        await(() -> controller.state() == YoBleScanController.ScanState.SCANNING, 3000);
        int startsBefore = platform.startCount.get();

        platform.callback.onPlatformScanFailed(
                YoBleScanAdvertiseStatus.SCAN_FAILED_FEATURE_UNSUPPORTED, "hw-unsupported");

        await(() -> platform.startCount.get() > startsBefore, 3000);
        assertEquals("降级重试不应直接把失败抛给上层", 0, failures.get());
        runtime.release();
    }

    @Test
    public void statsCountStartsFoundsAndStops() throws Exception {
        FakePlatform platform = new FakePlatform();
        YoBleRuntime runtime = newRuntime();
        YoBleScanController controller = newController(runtime, platform);

        controller.scan(quickConfig(), new BleScanObserver() {
        });
        await(() -> controller.state() == YoBleScanController.ScanState.SCANNING, 3000);
        assertEquals(1L, controller.stats().starts());

        platform.callback.onScanResult(scanResult());
        await(() -> controller.stats().founds() >= 1, 2000);

        controller.stopScan();
        await(() -> controller.stats().stops() >= 1, 2000);
        runtime.release();
    }

    @Test
    public void emitsLostAfterDeviceStopsAdvertising() throws Exception {
        FakePlatform platform = new FakePlatform();
        YoBleRuntime runtime = newRuntime();
        AtomicLong clock = new AtomicLong(1_000L);
        YoBleScanController controller = newController(runtime, platform, clock::get);

        AtomicInteger lostCount = new AtomicInteger();
        controller.scan(lostConfig(), new BleScanObserver() {
            @Override
            public void onScanResultLost(@NonNull BleScanResult lostResult) {
                lostCount.incrementAndGet();
            }
        });
        await(() -> controller.state() == YoBleScanController.ScanState.SCANNING, 3000);

        platform.callback.onScanResult(scanResult());
        await(() -> controller.activeDeviceCount() >= 1, 3000);

        // 末次广播后越过过期阈值：软件 lost 清扫应产生 onScanResultLost 并移除该设备。
        clock.addAndGet(60_000L);
        await(() -> lostCount.get() >= 1, 5000);
        assertEquals(1, lostCount.get());
        assertEquals(0, controller.activeDeviceCount());
        runtime.release();
    }

    private static BleScanResult scanResult() {
        return new BleScanResult(
                new BleDeviceIdentity(new BleDeviceId("AA:BB:CC:00:00:09"), "dev", 0L),
                -50,
                0L);
    }

    @NonNull
    private static YoBleRuntime newRuntime() {
        return new YoBleRuntime(YoBleRuntimeConfig.builder()
                .callbackOnMainThread(false)
                .build());
    }

    @NonNull
    private static YoBleScanController newController(@NonNull YoBleRuntime runtime,
                                                     @NonNull YoBleScanPlatform platform) {
        return newController(runtime, platform, System::currentTimeMillis);
    }

    @NonNull
    private static YoBleScanController newController(@NonNull YoBleRuntime runtime,
                                                     @NonNull YoBleScanPlatform platform,
                                                     @NonNull LongSupplier clockMillis) {
        return new YoBleScanController(
                runtime,
                BleConfig.builder().callbackOnMainThread(false).build(),
                new FakeInspector(),
                platform,
                new YoBleDeviceSnapshotStore(),
                new YoBleTimelineRecorder(16),
                clockMillis);
    }

    @NonNull
    private static BleScanConfig quickConfig() {
        return BleScanConfig.builder()
                .timeoutMillis(0L)
                .reportDelayMillis(0L)
                .build();
    }

    @NonNull
    private static BleScanConfig lostConfig() {
        return BleScanConfig.builder()
                .timeoutMillis(0L)
                .reportDelayMillis(0L)
                .matchLostTimeoutMillis(1_000L)
                .build();
    }

    private static void await(BooleanSupplier condition, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(10L);
        }
        assertTrue("条件未在超时内满足", condition.getAsBoolean());
    }

    private static final class FakePlatform implements YoBleScanPlatform {
        final AtomicInteger startCount = new AtomicInteger();
        final AtomicInteger stopCount = new AtomicInteger();
        volatile boolean started;
        volatile String startThreadName;
        volatile PlatformCallback callback;
        @NonNull
        YoBleScanPlatform.StartResult nextStart = YoBleScanPlatform.StartResult.ok();

        @NonNull
        @Override
        public YoBleScanPlatform.StartResult startPlatformScan(@NonNull BleScanConfig config,
                                                               @NonNull PlatformCallback callback) {
            this.callback = callback;
            startThreadName = Thread.currentThread().getName();
            startCount.incrementAndGet();
            started = nextStart.success;
            return nextStart;
        }

        @Override
        public void stopPlatformScan() {
            stopCount.incrementAndGet();
            started = false;
        }

        @Override
        public boolean flushPendingScanResults() {
            return false;
        }

        @Override
        public boolean supportsHardwareBatching() {
            return false;
        }

        @Override
        public boolean supportsOffloadedFiltering() {
            return false;
        }

        @Override
        public boolean supportsExtendedAdvertising() {
            return false;
        }
    }

    private static final class FakeInspector implements YoBleCapabilityInspector {
        @Override
        public boolean isBleSupported() {
            return true;
        }

        @Override
        public boolean isAdapterEnabled() {
            return true;
        }

        @NonNull
        @Override
        public BleAdapterState adapterState() {
            return BleAdapterState.ON;
        }

        @Override
        public boolean hasScanPermission() {
            return true;
        }

        @Override
        public boolean hasConnectPermission() {
            return true;
        }

        @Override
        public boolean hasAdvertisePermission() {
            return true;
        }

        @Override
        public boolean hasLocationPermission() {
            return true;
        }

        @Override
        public boolean isLocationServicesEnabled() {
            return true;
        }

        @Override
        public int sdkInt() {
            return 35;
        }
    }
}
