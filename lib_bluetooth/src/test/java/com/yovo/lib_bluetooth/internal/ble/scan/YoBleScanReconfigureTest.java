package com.yovo.lib_bluetooth.internal.ble.scan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.api.BleScanCapabilities;
import com.yovo.lib_bluetooth.api.BleScanHealth;
import com.yovo.lib_bluetooth.api.BleScanPlan;
import com.yovo.lib_bluetooth.internal.ble.device.YoBleCapabilityInspector;
import com.yovo.lib_bluetooth.internal.ble.device.YoBleDeviceSnapshotStore;
import com.yovo.lib_bluetooth.internal.ble.diagnostics.YoBleTimelineRecorder;
import com.yovo.lib_bluetooth.internal.ble.runtime.YoBleRuntime;
import com.yovo.lib_bluetooth.internal.ble.runtime.YoBleRuntimeConfig;
import com.yovo.lib_bluetooth.model.ble.BleAdapterState;
import com.yovo.lib_bluetooth.model.ble.BleConfig;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleDeviceIdentity;
import com.yovo.lib_bluetooth.model.ble.BleErrorCode;
import com.yovo.lib_bluetooth.model.ble.BleFailure;
import com.yovo.lib_bluetooth.model.ble.BleScanMode;
import com.yovo.lib_bluetooth.model.ble.BleScanObserver;
import com.yovo.lib_bluetooth.model.ble.BleScanResult;
import com.yovo.lib_bluetooth.model.common.Result;

import org.junit.After;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/**
 * 平台扫描参数变更（{@code reconfigure}）与限流守卫/健康度的机制语义。
 *
 * <p>覆盖：重配幂等不重扫、参数变化才重扫、无会话失败、窗口内第 6 次启动被拒且无平台调用、
 * 健康度字段语义、能力查询来自平台。</p>
 */
public final class YoBleScanReconfigureTest {

    @After
    public void resetHub() {
        YoBleScanHub.resetForTest();
    }

    @Test
    public void reconfigureWithSamePlanIsIdempotentAndDoesNotRestart() throws Exception {
        FakePlatform platform = new FakePlatform();
        AtomicLong clock = new AtomicLong(1_000L);
        YoBleRuntime runtime = newRuntime();
        YoBleScanController controller = newController(runtime, platform, clock);

        controller.scan(YoBleScanDefaults.config(), new BleScanObserver() {
        });
        await(controller::isScanning, 3000);
        int startsBeforeReconfigure = platform.startCount.get();

        Result<Void> result = controller.reconfigure(BleScanPlan.defaults());

        assertTrue("参数一致必须返回成功", result.isSuccess());
        Thread.sleep(150L);
        assertEquals("参数一致不得重扫", startsBeforeReconfigure, platform.startCount.get());
        assertTrue(controller.isScanning());
        runtime.release();
    }

    @Test
    public void reconfigureWithChangedPlanRestartsExactlyOnce() throws Exception {
        FakePlatform platform = new FakePlatform();
        AtomicLong clock = new AtomicLong(1_000L);
        YoBleRuntime runtime = newRuntime();
        YoBleScanController controller = newController(runtime, platform, clock);

        controller.scan(YoBleScanDefaults.config(), new BleScanObserver() {
        });
        await(controller::isScanning, 3000);
        int startsBeforeReconfigure = platform.startCount.get();

        Result<Void> result = controller.reconfigure(
                new BleScanPlan(BleScanMode.LOW_POWER, 500L, true, List.of()));

        assertTrue(result.isSuccess());
        await(() -> platform.startCount.get() == startsBeforeReconfigure + 1, 3000);
        assertEquals("参数变化只允许重启一次", startsBeforeReconfigure + 1, platform.startCount.get());
        assertTrue("重配后仍在扫描", controller.isScanning());
        runtime.release();
    }

    @Test
    public void reconfigureWithoutActiveSessionFails() {
        FakePlatform platform = new FakePlatform();
        AtomicLong clock = new AtomicLong(1_000L);
        YoBleRuntime runtime = newRuntime();
        YoBleScanController controller = newController(runtime, platform, clock);

        Result<Void> result = controller.reconfigure(BleScanPlan.defaults());

        assertFalse("无会话时重配必须失败", result.isSuccess());
        assertEquals("不得发起平台扫描", 0, platform.startCount.get());
        runtime.release();
    }

    @Test
    public void sixthStartInsideWindowIsRejectedWithoutPlatformCall() throws Exception {
        FakePlatform platform = new FakePlatform();
        // 时钟冻结在窗口内：5 次启动之后，第 6 次必须被守卫拒绝。
        AtomicLong clock = new AtomicLong(5_000L);
        YoBleRuntime runtime = newRuntime();
        YoBleScanController controller = newController(runtime, platform, clock);

        for (int i = 1; i <= 5; i++) {
            final int expected = i;
            controller.scan(YoBleScanDefaults.config(), new BleScanObserver() {
            });
            await(() -> controller.health().startsInLastWindow() == expected, 3000);
        }
        assertEquals(5, platform.startCount.get());

        AtomicReference<BleFailure> failure = new AtomicReference<>();
        controller.scan(YoBleScanDefaults.config(), new BleScanObserver() {
            @Override
            public void onScanFailed(@NonNull BleFailure f) {
                failure.set(f);
            }
        });

        await(() -> failure.get() != null, 3000);
        assertNotNull("第 6 次必须失败而不是静默", failure.get());
        assertEquals(BleErrorCode.SCAN_THROTTLED, failure.get().errorCode());
        assertEquals("被守卫拒绝时不得调用平台 startScan", 5, platform.startCount.get());
        assertEquals(5, controller.health().startsInLastWindow());
        assertTrue("拒绝不得打断已经在跑的会话", controller.isScanning());
        assertEquals(YoBleScanController.ScanState.SCANNING, controller.state());
        runtime.release();
    }

    @Test
    public void reconfigureIsRejectedOnceTheWindowIsExhausted() throws Exception {
        FakePlatform platform = new FakePlatform();
        AtomicLong clock = new AtomicLong(5_000L);
        YoBleRuntime runtime = newRuntime();
        YoBleScanController controller = newController(runtime, platform, clock);

        for (int i = 1; i <= 5; i++) {
            final int expected = i;
            controller.scan(YoBleScanDefaults.config(), new BleScanObserver() {
            });
            await(() -> controller.health().startsInLastWindow() == expected, 3000);
        }
        int startsBeforeReconfigure = platform.startCount.get();

        Result<Void> result = controller.reconfigure(
                new BleScanPlan(BleScanMode.BALANCED, 0L, false, List.of()));

        assertFalse("窗口耗尽后重配必须被拒绝", result.isSuccess());
        Thread.sleep(150L);
        assertEquals("被拒绝的重配不得调用平台扫描", startsBeforeReconfigure, platform.startCount.get());
        runtime.release();
    }

    @Test
    public void healthReportsStartTimeLastFrameAndAdapterState() throws Exception {
        FakePlatform platform = new FakePlatform();
        AtomicLong clock = new AtomicLong(7_000L);
        YoBleRuntime runtime = newRuntime();
        YoBleScanController controller = newController(runtime, platform, clock);

        BleScanHealth idle = controller.health();
        assertFalse(idle.scanning());
        assertEquals(0L, idle.startedAtMillis());
        assertEquals(0L, idle.lastFrameAtMillis());
        assertEquals(BleAdapterState.ON, idle.adapterState());

        controller.scan(YoBleScanDefaults.config(), new BleScanObserver() {
        });
        await(controller::isScanning, 3000);

        BleScanHealth running = controller.health();
        assertTrue(running.scanning());
        assertEquals("启动时刻取自注入时钟", 7_000L, running.startedAtMillis());
        assertEquals(1, running.startsInLastWindow());
        assertEquals("尚未收到平台帧", 0L, running.lastFrameAtMillis());

        clock.set(9_000L);
        platform.callback.onScanResult(frame());
        await(() -> controller.health().lastFrameAtMillis() == 9_000L, 3000);
        assertEquals(9_000L, controller.health().lastFrameAtMillis());
        runtime.release();
    }

    @Test
    public void capabilitiesComeFromThePlatformQueries() {
        FakePlatform platform = new FakePlatform();
        platform.offloadedFiltering = true;
        platform.extendedAdvertising = true;
        YoBleRuntime runtime = newRuntime();
        YoBleScanController controller = newController(runtime, platform, new AtomicLong(1L));

        BleScanCapabilities capabilities = controller.capabilities();

        assertTrue(capabilities.offloadedFiltering());
        assertFalse("批量能力由平台查询给出", capabilities.batching());
        assertTrue(capabilities.extendedAdvertising());
        runtime.release();
    }

    // ── 辅助 ─────────────────────────────────────────────────────

    @NonNull
    private static YoBleRuntime newRuntime() {
        return new YoBleRuntime(YoBleRuntimeConfig.builder().callbackOnMainThread(false).build());
    }

    @NonNull
    private static YoBleScanController newController(@NonNull YoBleRuntime runtime,
                                                     @NonNull FakePlatform platform,
                                                     @NonNull AtomicLong clock) {
        return new YoBleScanController(
                runtime,
                BleConfig.builder().callbackOnMainThread(false).build(),
                new FakeInspector(),
                platform,
                new YoBleDeviceSnapshotStore(),
                new YoBleTimelineRecorder(16),
                clock::get);
    }

    private static BleScanResult frame() {
        return new BleScanResult(
                new BleDeviceIdentity(new BleDeviceId("AA:BB:CC:00:00:02"), "dev", 0L),
                -55,
                0L);
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
        volatile PlatformCallback callback;
        volatile boolean started;
        volatile boolean offloadedFiltering;
        volatile boolean extendedAdvertising;

        @NonNull
        @Override
        public YoBleScanPlatform.StartResult startPlatformScan(@NonNull BleScanConfig config,
                                                               @NonNull PlatformCallback callback) {
            this.callback = callback;
            startCount.incrementAndGet();
            started = true;
            return YoBleScanPlatform.StartResult.ok();
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
            return offloadedFiltering;
        }

        @Override
        public boolean supportsExtendedAdvertising() {
            return extendedAdvertising;
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
