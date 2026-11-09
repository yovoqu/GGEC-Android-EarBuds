package com.yovo.lib_bluetooth.internal.ble.scan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_bluetooth.internal.ble.testing.FakeYoBleHarness;
import com.yovo.lib_bluetooth.model.ble.BleErrorCode;
import com.yovo.lib_bluetooth.model.ble.BleFailure;
import com.yovo.lib_bluetooth.model.ble.BleOperationHandle;
import com.yovo.lib_bluetooth.model.ble.BleScanConfig;
import com.yovo.lib_bluetooth.model.ble.BleScanObserver;
import com.yovo.lib_bluetooth.model.ble.BleScanThrottlePolicy;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * BLE-006 Android 7+ 30 秒 5 次扫描防限流滑窗验证：
 * 验证 29,999ms 边界拦截、30,000ms 释放、FAIL_FAST、DELAY_UNTIL_WINDOW_FREE 自动延时执行，
 * 以及取消等待中扫描无定时器泄漏。
 */
public class BleScanThrottleGuardTest {

    @Test
    public void failFast_enforcesExact5In30sBoundaryAt29999vs30000Ms() {
        FakeYoBleHarness harness = new FakeYoBleHarness();
        try {
            BleScanConfig failFastCfg = BleScanConfig.builder()
                    .throttlePolicy(BleScanThrottlePolicy.FAIL_FAST)
                    .timeoutMillis(0L)
                    .build();

            for (int i = 0; i < 5; i++) {
                AtomicBoolean started = new AtomicBoolean(false);
                harness.client.scan(failFastCfg, new BleScanObserver() {
                    @Override
                    public void onScanStarted() {
                        started.set(true);
                    }
                });
                harness.drain();
                assertTrue("Scan #" + (i + 1) + " should start immediately", started.get());
                harness.client.stopScan();
                harness.drain();
            }
            assertEquals(5, harness.scanPlatform.startCount());

            // 推进 29,999ms：仍处于 30_000ms 窗口内，第 6 次立即返回 SCAN_THROTTLED 且 wait>=1ms
            harness.advanceMillis(29_999L);
            AtomicReference<BleFailure> throttled = new AtomicReference<>();
            harness.client.scan(failFastCfg, new BleScanObserver() {
                @Override
                public void onScanFailed(BleFailure failure) {
                    throttled.set(failure);
                }
            });
            harness.drain();
            assertNotNull(throttled.get());
            assertEquals(BleErrorCode.SCAN_THROTTLED, throttled.get().errorCode());
            assertTrue(throttled.get().message().contains("1ms"));
            assertEquals(5, harness.scanPlatform.startCount());

            // 再推进 1ms（合计 30,000ms）：窗口释放，允许第 6 次启动
            harness.advanceMillis(1L);
            AtomicBoolean sixthStarted = new AtomicBoolean(false);
            harness.client.scan(failFastCfg, new BleScanObserver() {
                @Override
                public void onScanStarted() {
                    sixthStarted.set(true);
                }
            });
            harness.drain();
            assertTrue(sixthStarted.get());
            assertEquals(6, harness.scanPlatform.startCount());
        } finally {
            harness.release();
        }
    }

    @Test
    public void delayUntilWindowFree_waitsAndLaunchesAutomatically_andCancelCleansTimer() {
        FakeYoBleHarness harness = new FakeYoBleHarness();
        try {
            BleScanConfig delayCfg = BleScanConfig.builder()
                    .throttlePolicy(BleScanThrottlePolicy.DELAY_UNTIL_WINDOW_FREE)
                    .timeoutMillis(5_000L)
                    .build();

            for (int i = 0; i < 5; i++) {
                harness.client.scan(delayCfg, new BleScanObserver() {});
                harness.drain();
            }
            assertEquals(5, harness.scanPlatform.startCount());

            // 短时间再触发第 6 次（连续调用）：等待窗口空出，尚未启动第 6 次
            AtomicBoolean sixthStarted = new AtomicBoolean(false);
            harness.advanceMillis(1_000L);
            BleOperationHandle delayedHandle = harness.client.scan(delayCfg, new BleScanObserver() {
                @Override
                public void onScanStarted() {
                    sixthStarted.set(true);
                }
            });
            harness.drain();
            assertFalse(sixthStarted.get());
            assertEquals(5, harness.scanPlatform.startCount());

            // 取消等待中的第 6 次，确认时间到达后不再泄漏启动
            delayedHandle.cancel();
            harness.drain();
            harness.advanceMillis(35_000L);
            assertFalse("Cancelled delayed scan must not launch", sixthStarted.get());
            assertEquals(5, harness.scanPlatform.startCount());

            // 窗口已清空后再立即发两次，先清旧扫描并顺利发起
            harness.client.scan(delayCfg, new BleScanObserver() {});
            harness.drain();
            AtomicReference<BleFailure> err = new AtomicReference<>();
            AtomicBoolean eighthStarted = new AtomicBoolean(false);
            harness.client.scan(delayCfg, new BleScanObserver() {
                @Override
                public void onScanStarted() {
                    eighthStarted.set(true);
                }

                @Override
                public void onScanFailed(BleFailure failure) {
                    err.set(failure);
                }
            });
            harness.drain();
            assertNull(err.get());
            assertTrue(eighthStarted.get());
            assertEquals(7, harness.scanPlatform.startCount());
        } finally {
            harness.release();
        }
    }
}
