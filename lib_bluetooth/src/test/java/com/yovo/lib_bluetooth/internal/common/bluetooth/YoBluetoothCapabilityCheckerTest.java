package com.yovo.lib_bluetooth.internal.common.bluetooth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_bluetooth.internal.ble.device.YoBleCapabilityInspector;
import com.yovo.lib_bluetooth.internal.ble.testing.FakeYoBleHarness;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * BLE-002 BLE 硬件能力与适配器检查测试：
 * 覆盖 null Context 安全降级、适配器关闭时硬件支持判断，以及 Classic/BLE 互不干扰契约。
 */
public class YoBluetoothCapabilityCheckerTest {

    @Test
    public void nullContext_returnsFalseAndNoneWithoutCrash() {
        YoBluetoothCapabilityChecker checker = new YoBluetoothCapabilityChecker(null);
        assertFalse(checker.supportsClassic());
        assertFalse(checker.supportsBle());
        assertEquals(YoBluetoothCapabilityChecker.Capability.NONE, checker.capability());
    }

    @Test
    public void adapterDisabledButBleHardwareSupported_stillReportsBleSupportedTrue() {
        FakeYoBleHarness harness = new FakeYoBleHarness();
        try {
            harness.env.setBleSupported(true).setAdapterEnabled(false);
            assertTrue(harness.client.isBleSupported());
            assertFalse(harness.client.isAdapterEnabled());

            harness.env.setBleSupported(false).setAdapterEnabled(true);
            assertFalse(harness.client.isBleSupported());
            assertTrue(harness.client.isAdapterEnabled());
        } finally {
            harness.release();
        }
    }

    @Test
    public void capabilityInspector_delegatesAllEnvironmentSignals() {
        FakeYoBleHarness harness = new FakeYoBleHarness();
        try {
            harness.env.setBleSupported(true)
                    .setAdapterEnabled(false)
                    .setLocationServicesEnabled(false)
                    .setSdkInt(31);
            YoBleCapabilityInspector inspector = new YoBleCapabilityInspector(harness.env);
            assertTrue(inspector.isBleSupported());
            assertFalse(inspector.isAdapterEnabled());
            assertFalse(inspector.isLocationServicesEnabled());
            assertEquals(31, inspector.sdkInt());
        } finally {
            harness.release();
        }
    }

    @Test
    public void supportsBle_prioritizesSystemFeatureCheckEvenWhenAdapterOff() throws IOException {
        File direct = new File("src/main/java/com/yovo/lib_bluetooth/internal/common/bluetooth/YoBluetoothCapabilityChecker.java");
        File file = direct.exists()
                ? direct
                : new File("lib_bluetooth/src/main/java/com/yovo/lib_bluetooth/internal/common/bluetooth/YoBluetoothCapabilityChecker.java");
        String src = Files.readString(file.toPath(), StandardCharsets.UTF_8);

        int featureIdx = src.indexOf("PackageManager.FEATURE_BLUETOOTH_LE");
        int scannerIdx = src.indexOf("getBluetoothLeScanner");
        assertTrue("FEATURE_BLUETOOTH_LE check must exist", featureIdx > 0);
        assertTrue("FEATURE_BLUETOOTH_LE check must precede scanner fallback", featureIdx < scannerIdx);
    }
}
