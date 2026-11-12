package com.yovo.lib_bluetooth.internal.ble.scan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleDeviceIdentity;
import com.yovo.lib_bluetooth.model.ble.BleScanCallbackType;
import com.yovo.lib_bluetooth.model.ble.BleScanResult;

import org.junit.Test;

import java.util.List;

public final class YoBleScanCallbackEmulatorTest {

    @Test
    public void allMatchesEmitsEveryResultWhenNoDedupWindow() {
        YoBleScanCallbackEmulator emulator = new YoBleScanCallbackEmulator();
        BleScanConfig config = BleScanConfig.builder()
                .callbackType(BleScanCallbackType.ALL_MATCHES)
                .deduplicationWindowMillis(0L)
                .build();
        List<BleScanResult> out = emulator.onResult(config, result(), 0L);
        assertEquals(1, out.size());
        assertEquals(BleScanCallbackType.ALL_MATCHES, out.get(0).callbackType());
    }

    @Test
    public void sweepLostWorksForAllMatchesWhenTimeoutConfigured() {
        YoBleScanCallbackEmulator emulator = new YoBleScanCallbackEmulator();
        BleScanConfig config = BleScanConfig.builder()
                .callbackType(BleScanCallbackType.ALL_MATCHES)
                .deduplicationWindowMillis(0L)
                .matchLostTimeoutMillis(1000L)
                .build();
        emulator.onResult(config, result(), 0L);

        assertTrue("未过期的设备不应判丢", emulator.sweepLost(config, 500L).isEmpty());
        List<BleScanResult> lost = emulator.sweepLost(config, 1500L);
        assertEquals(1, lost.size());
        assertEquals(BleScanCallbackType.MATCH_LOST, lost.get(0).callbackType());
    }

    @Test
    public void sweepLostNoopBeforeTimeout() {
        YoBleScanCallbackEmulator emulator = new YoBleScanCallbackEmulator();
        BleScanConfig config = BleScanConfig.builder()
                .callbackType(BleScanCallbackType.ALL_MATCHES)
                .matchLostTimeoutMillis(1000L)
                .build();
        emulator.onResult(config, result(), 0L);
        assertTrue(emulator.sweepLost(config, 999L).isEmpty());
    }

    @Test
    public void evictionPrefersLeastRecentlySeen() {
        YoBleScanCallbackEmulator emulator = new YoBleScanCallbackEmulator();
        BleScanConfig config = BleScanConfig.builder()
                .callbackType(BleScanCallbackType.ALL_MATCHES)
                .deduplicationWindowMillis(0L)
                .build();
        for (int i = 0; i < 256; i++) {
            emulator.onResult(config, result(i), i);
        }
        emulator.onResult(config, result(0), 300L);    // 刷新 0 的 lastSeen
        emulator.onResult(config, result(256), 400L);  // 触发一次驱逐

        assertEquals(256, emulator.activeDeviceCount());
        assertTrue("活跃设备(刚刷新)不应被逐出", emulator.contains(id(0)));
        assertFalse("最久未见设备应被逐出", emulator.contains(id(1)));
    }

    private static BleScanResult result() {
        return result(0);
    }

    private static BleScanResult result(int i) {
        return new BleScanResult(
                new BleDeviceIdentity(id(i), "dev" + i, 0L),
                -50,
                0L);
    }

    private static BleDeviceId id(int i) {
        return new BleDeviceId(String.format("AA:BB:CC:%02X:%02X:%02X",
                (i >> 16) & 0xFF, (i >> 8) & 0xFF, i & 0xFF));
    }
}
