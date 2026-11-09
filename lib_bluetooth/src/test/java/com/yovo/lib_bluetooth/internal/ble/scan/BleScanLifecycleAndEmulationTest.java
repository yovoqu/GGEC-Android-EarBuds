package com.yovo.lib_bluetooth.internal.ble.scan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_bluetooth.internal.ble.testing.FakeYoBleHarness;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleDeviceIdentity;
import com.yovo.lib_bluetooth.model.ble.BleOperationHandle;
import com.yovo.lib_bluetooth.model.ble.BleScanCallbackType;
import com.yovo.lib_bluetooth.model.ble.BleScanConfig;
import com.yovo.lib_bluetooth.model.ble.BleScanObserver;
import com.yovo.lib_bluetooth.model.ble.BleScanResult;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * BLE-009 扫描回调仿真（FIRST_MATCH / MATCH_LOST / 去重窗口 / Batch / 超时停扫 / 空闲幂等）测试。
 */
public class BleScanLifecycleAndEmulationTest {

    @Test
    public void firstMatchAndMatchLostEmulation_expiresAfterExactLostThreshold() {
        FakeYoBleHarness harness = new FakeYoBleHarness();
        try {
            List<BleScanResult> found = new ArrayList<>();
            List<BleScanResult> lost = new ArrayList<>();
            AtomicInteger stopCount = new AtomicInteger();

            BleScanConfig config = BleScanConfig.builder()
                    .callbackType(BleScanCallbackType.FIRST_MATCH_AND_LOST)
                    .matchLostTimeoutMillis(3_000L)
                    .timeoutMillis(10_000L)
                    .build();

            harness.client.scan(config, new BleScanObserver() {
                @Override
                public void onDeviceFound(BleScanResult result) {
                    found.add(result);
                }

                @Override
                public void onScanResultLost(BleScanResult lostResult) {
                    lost.add(lostResult);
                }

                @Override
                public void onScanStopped() {
                    stopCount.incrementAndGet();
                }
            });
            harness.drain();

            BleScanResult r1 = scanResult("AA:BB:CC:DD:EE:10", -55, harness.clock.nowMillis());
            harness.scanPlatform.emitResult(r1);
            harness.advanceMillis(500L);
            harness.scanPlatform.emitResult(scanResult("AA:BB:CC:DD:EE:10", -52, harness.clock.nowMillis()));
            harness.drain();

            // FIRST_MATCH 每个设备只回调 1 次
            assertEquals(1, found.size());
            assertEquals(BleScanCallbackType.FIRST_MATCH, found.get(0).callbackType());
            assertEquals(0, lost.size());

            // 再推进 2,499ms (距最后见包 2,999ms) 尚未丢失
            harness.advanceMillis(2_499L);
            assertEquals(0, lost.size());

            // 推进至超出 3,000ms 扫查周期触发 MATCH_LOST
            harness.advanceMillis(1_001L);
            assertEquals(1, lost.size());
            assertEquals(BleScanCallbackType.MATCH_LOST, lost.get(0).callbackType());
            assertEquals("AA:BB:CC:DD:EE:10", lost.get(0).identity().deviceId().value());

            // 超时 10_000ms 自动停止并回调 onScanStopped 一次
            harness.advanceMillis(7_000L);
            assertFalse(harness.client.isScanning());
            assertEquals(1, stopCount.get());
        } finally {
            harness.release();
        }
    }

    @Test
    public void deduplicationWindow_batchFlush_andManualCancelStopsCleanlyOnce() {
        FakeYoBleHarness harness = new FakeYoBleHarness();
        try {
            List<BleScanResult> found = new ArrayList<>();
            List<List<BleScanResult>> batches = new ArrayList<>();
            AtomicInteger stopped = new AtomicInteger();

            BleScanConfig cfg = BleScanConfig.builder()
                    .deduplicationWindowMillis(1_000L)
                    .timeoutMillis(5_000L)
                    .build();

            BleOperationHandle handle = harness.client.scan(cfg, new BleScanObserver() {
                @Override
                public void onDeviceFound(BleScanResult result) {
                    found.add(result);
                }

                @Override
                public void onBatchScanResults(List<BleScanResult> results) {
                    batches.add(results);
                }

                @Override
                public void onScanStopped() {
                    stopped.incrementAndGet();
                }
            });
            harness.drain();

            // 去重节流：0ms 收，500ms 忽略，1000ms 再次放行
            harness.scanPlatform.emitResult(scanResult("AA:BB:CC:DD:EE:20", -60, harness.clock.nowMillis()));
            harness.drain();
            harness.advanceMillis(500L);
            harness.scanPlatform.emitResult(scanResult("AA:BB:CC:DD:EE:20", -58, harness.clock.nowMillis()));
            harness.drain();
            assertEquals(1, found.size());

            harness.advanceMillis(500L);
            harness.scanPlatform.emitResult(scanResult("AA:BB:CC:DD:EE:20", -56, harness.clock.nowMillis()));
            harness.drain();
            assertEquals(2, found.size());

            // Batch 聚合并触发 flushBatchScanResults
            assertTrue(harness.client.flushBatchScanResults().isSuccess());
            assertEquals(1, harness.scanPlatform.flushCount());
            harness.scanPlatform.emitBatch(Arrays.asList(
                    scanResult("AA:BB:CC:DD:EE:21", -50, harness.clock.nowMillis()),
                    scanResult("AA:BB:CC:DD:EE:22", -52, harness.clock.nowMillis())));
            harness.drain();
            assertEquals(1, batches.size());
            assertEquals(2, batches.get(0).size());

            // 超时前手动 cancel() -> 立即停止且同步取消 timeout，后续时间流逝不再重复回调 onScanStopped
            handle.cancel();
            harness.drain();
            assertFalse(harness.client.isScanning());
            assertEquals(1, stopped.get());

            harness.advanceMillis(10_000L);
            assertEquals(1, stopped.get());

            // 空闲状态下调用 stopScan 幂等
            assertTrue(harness.client.stopScan().isSuccess());
            harness.drain();
            assertEquals(1, stopped.get());
        } finally {
            harness.release();
        }
    }

    private static BleScanResult scanResult(String mac, int rssi, long ts) {
        return new BleScanResult(
                new BleDeviceIdentity(new BleDeviceId(mac), "Dev", ts),
                rssi, ts);
    }

    @Test
    public void compatAndPlatformContract_allMatchesMaskLengthGuardAndFallbackPresent() throws IOException {
        File moduleRoot = new File("src/main/java").isDirectory() ? new File(".") : new File("lib_bluetooth");
        String compatSrc = Files.readString(new File(moduleRoot,
                "src/main/java/com/yovo/lib_bluetooth/internal/ble/internal/android/compat/YoBleScanCompat.java")
                .toPath(), StandardCharsets.UTF_8);
        String platformSrc = Files.readString(new File(moduleRoot,
                "src/main/java/com/yovo/lib_bluetooth/internal/ble/internal/android/YoBleAndroidScanPlatform.java")
                .toPath(), StandardCharsets.UTF_8);
        String controllerSrc = Files.readString(new File(moduleRoot,
                "src/main/java/com/yovo/lib_bluetooth/internal/ble/scan/YoBleScanController.java")
                .toPath(), StandardCharsets.UTF_8);

        assertTrue(compatSrc.contains("ScanSettings.CALLBACK_TYPE_ALL_MATCHES"));
        assertTrue(compatSrc.contains("toFallbackScanSettings"));
        assertTrue(compatSrc.contains("f.serviceDataMask().length == f.serviceData().length"));
        assertTrue(compatSrc.contains("f.manufacturerDataMask().length == f.manufacturerData().length"));
        assertTrue(platformSrc.contains("toFallbackScanSettings(config)"));
        assertTrue(controllerSrc.contains("private volatile boolean scanning"));
        assertTrue(controllerSrc.contains("private volatile boolean released"));
    }
}
