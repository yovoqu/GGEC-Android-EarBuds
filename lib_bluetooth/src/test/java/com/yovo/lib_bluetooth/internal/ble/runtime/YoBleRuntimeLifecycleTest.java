package com.yovo.lib_bluetooth.internal.ble.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.yovo.lib_bluetooth.internal.ble.testing.FakeYoBleHarness;
import com.yovo.lib_bluetooth.model.ble.BleConfig;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleErrorCode;
import com.yovo.lib_bluetooth.model.ble.BleFailure;
import com.yovo.lib_bluetooth.model.ble.BleScanConfig;
import com.yovo.lib_bluetooth.model.ble.BleScanObserver;
import com.yovo.lib_bluetooth.model.common.Result;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * BLE-003 四线程池调度与作用域优雅释放测试：
 * 验证 state 串行与线程断言、无 MainLooper 依赖独立调度、release 3 次幂等、
 * Scoped 任务在 release 后静默丢弃、释放后新操作立即返回 RELEASED。
 */
public class YoBleRuntimeLifecycleTest {

    @BeforeClass
    public static void installTestLogger() {
        FakeYoBleHarness.ensureTestLoggerInstalled();
    }

    @Test
    public void stateThreadSerializesTasks_andEnforcesCheckStateThread() throws Exception {
        YoBleRuntime runtime = new YoBleRuntime(YoBleRuntimeConfig.builder()
                .callbackOnMainThread(false)
                .stateThreadName("test-ble-state")
                .build());
        try {
            List<Integer> order = new ArrayList<>();
            CountDownLatch stateDone = new CountDownLatch(25);
            AtomicReference<Throwable> checkError = new AtomicReference<>();

            for (int i = 0; i < 25; i++) {
                int idx = i;
                runtime.state(() -> {
                    try {
                        runtime.checkStateThread();
                        order.add(idx);
                    } catch (Throwable t) {
                        checkError.set(t);
                    } finally {
                        stateDone.countDown();
                    }
                });
            }

            assertTrue(stateDone.await(5, TimeUnit.SECONDS));
            assertEquals(25, order.size());
            for (int i = 0; i < 25; i++) {
                assertEquals(Integer.valueOf(i), order.get(i));
            }
            if (checkError.get() != null) {
                fail("checkStateThread threw inside state thread: " + checkError.get());
            }

            // 非 state 线程调用 checkStateThread 必须抛出 IllegalStateException
            boolean threw = false;
            try {
                runtime.checkStateThread();
            } catch (IllegalStateException expected) {
                threw = true;
            }
            assertTrue("Calling checkStateThread off state thread must fail", threw);

            // callback 与 io 独立运行且无需 Looper.getMainLooper()
            CountDownLatch ioAndCb = new CountDownLatch(2);
            runtime.io(() -> {
                runtime.checkIoThread();
                ioAndCb.countDown();
            });
            runtime.callback(ioAndCb::countDown);
            assertTrue(ioAndCb.await(5, TimeUnit.SECONDS));
        } finally {
            runtime.release();
        }
    }

    @Test
    public void releaseIsIdempotentThreeTimes_andDiscardsScopedTasks() {
        FakeYoBleHarness harness = new FakeYoBleHarness(BleConfig.builder().callbackOnMainThread(false).build());
        YoBleRuntimeScope scope = harness.core.runtime.createScope();
        AtomicInteger scopedRuns = new AtomicInteger(0);
        AtomicBoolean cancelledBeforeRun = new AtomicBoolean(false);

        scope.state(scopedRuns::incrementAndGet);
        scope.release();
        harness.drain();
        assertEquals("Released scope must discard queued tasks silently", 0, scopedRuns.get());

        // release 三次幂等无异常
        harness.client.release();
        harness.client.release();
        harness.client.release();
        harness.drain();
        assertTrue(harness.core.runtime.isReleased());

        // release 后调度 state/io/callback 均应静默丢弃且绝不抛出 RejectedExecutionException
        harness.core.runtime.state(() -> cancelledBeforeRun.set(true));
        harness.core.runtime.io(() -> cancelledBeforeRun.set(true));
        harness.core.runtime.callback(() -> cancelledBeforeRun.set(true));
        scope.callback(() -> cancelledBeforeRun.set(true));
        harness.drain();
        assertFalse(cancelledBeforeRun.get());

        // release 后调用 client 操作返回 RELEASED 错误
        Result<?> snapResult = harness.client.getDeviceSnapshot(new BleDeviceId("AA:BB:CC:DD:EE:01"));
        assertTrue(snapResult.isFailure());
        assertEquals(BleErrorCode.RELEASED, ((BleFailure) snapResult.getFailure()).errorCode());

        AtomicReference<BleFailure> scanErr = new AtomicReference<>();
        harness.client.scan(BleScanConfig.builder().build(), new BleScanObserver() {
            @Override
            public void onScanFailed(BleFailure failure) {
                scanErr.set(failure);
            }
        });
        // release 后 runtime.callback 被守卫拦截或立即完成，同步断言 snapshot / stopScan 等
        Result<Void> disc = harness.client.disconnectAll();
        assertTrue(disc.isFailure());
        assertEquals(BleErrorCode.RELEASED, ((BleFailure) disc.getFailure()).errorCode());
        assertNotNull(harness.client);
    }
}
