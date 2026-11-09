package com.yovo.lib_bluetooth.internal.ble.scan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_bluetooth.internal.ble.testing.FakeYoBleHarness;
import com.yovo.lib_bluetooth.internal.common.permission.YoBluetoothPermission;
import com.yovo.lib_bluetooth.model.ble.BleErrorCode;
import com.yovo.lib_bluetooth.model.ble.BleFailure;
import com.yovo.lib_bluetooth.model.ble.BleScanConfig;
import com.yovo.lib_bluetooth.model.ble.BleScanObserver;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * BLE-005 扫描前置校验三维矩阵（API 24/30/31+ × 权限 × 位置开关 × 硬件/蓝牙开关）。
 */
public class BleScanPreflightTest {

    @Test
    public void hardwareUnsupported_rejectedWithBleNotSupported() {
        FakeYoBleHarness harness = new FakeYoBleHarness();
        try {
            harness.env.setBleSupported(false);
            BleFailure f = runPreflightScan(harness, BleScanConfig.builder().build());
            assertNotNull(f);
            assertEquals(BleErrorCode.BLE_NOT_SUPPORTED, f.errorCode());
            assertEquals(0, harness.scanPlatform.startCount());
        } finally {
            harness.release();
        }
    }

    @Test
    public void bluetoothDisabled_rejectedWithBluetoothDisabled() {
        FakeYoBleHarness harness = new FakeYoBleHarness();
        try {
            harness.env.setAdapterEnabled(false);
            BleFailure f = runPreflightScan(harness, BleScanConfig.builder().build());
            assertNotNull(f);
            assertEquals(BleErrorCode.BLUETOOTH_DISABLED, f.errorCode());
        } finally {
            harness.release();
        }
    }

    @Test
    public void missingScanPermission_rejectedWithPermissionScanDenied() {
        FakeYoBleHarness harness = new FakeYoBleHarness();
        try {
            harness.env.setSdkInt(31).deny(YoBluetoothPermission.SCAN);
            BleFailure f = runPreflightScan(harness, BleScanConfig.builder().build());
            assertNotNull(f);
            assertEquals(BleErrorCode.PERMISSION_SCAN_DENIED, f.errorCode());
        } finally {
            harness.release();
        }
    }

    @Test
    public void api24AndApi30_locationPermissionAndLocationServicesChecks() {
        for (int sdk : new int[]{24, 30}) {
            FakeYoBleHarness harness = new FakeYoBleHarness();
            try {
                // 缺少位置权限
                harness.env.setSdkInt(sdk)
                        .grant(YoBluetoothPermission.SCAN)
                        .deny(YoBluetoothPermission.LOCATION)
                        .setLocationServicesEnabled(true);
                BleFailure permErr = runPreflightScan(
                        harness, BleScanConfig.builder().locationExempt(true).build());
                assertNotNull(permErr);
                assertEquals("SDK " + sdk + " must require LOCATION even with locationExempt",
                        BleErrorCode.PERMISSION_LOCATION_DENIED, permErr.errorCode());

                // 有位置权限但系统位置开关关闭
                harness.env.grant(YoBluetoothPermission.LOCATION).setLocationServicesEnabled(false);
                BleFailure svcErr = runPreflightScan(
                        harness, BleScanConfig.builder().locationExempt(true).build());
                assertNotNull(svcErr);
                assertEquals(BleErrorCode.LOCATION_SERVICES_DISABLED, svcErr.errorCode());

                // 位置权限与系统位置均开启 -> 启动成功
                harness.env.setLocationServicesEnabled(true);
                assertStarted(harness, BleScanConfig.builder().locationExempt(true).build());
            } finally {
                harness.release();
            }
        }
    }

    @Test
    public void api31Plus_locationExemptSkipsLocationChecks_nonExemptEnforcesThem() {
        FakeYoBleHarness harness = new FakeYoBleHarness();
        try {
            harness.env.setSdkInt(31)
                    .grant(YoBluetoothPermission.SCAN)
                    .deny(YoBluetoothPermission.LOCATION)
                    .setLocationServicesEnabled(false);

            // locationExempt=true 豁免位置权限和开关
            assertStarted(harness, BleScanConfig.builder().locationExempt(true).build());
            harness.client.stopScan();
            harness.drain();

            // locationExempt=false 仍检查位置权限及开关
            BleFailure locPermErr = runPreflightScan(
                    harness, BleScanConfig.builder().locationExempt(false).build());
            assertNotNull(locPermErr);
            assertEquals(BleErrorCode.PERMISSION_LOCATION_DENIED, locPermErr.errorCode());

            harness.env.grant(YoBluetoothPermission.LOCATION);
            BleFailure locSvcErr = runPreflightScan(
                    harness, BleScanConfig.builder().locationExempt(false).build());
            assertNotNull(locSvcErr);
            assertEquals(BleErrorCode.LOCATION_SERVICES_DISABLED, locSvcErr.errorCode());
        } finally {
            harness.release();
        }
    }

    private static BleFailure runPreflightScan(FakeYoBleHarness harness, BleScanConfig config) {
        AtomicReference<BleFailure> err = new AtomicReference<>();
        harness.client.scan(config, new BleScanObserver() {
            @Override
            public void onScanFailed(BleFailure failure) {
                err.set(failure);
            }
        });
        harness.drain();
        return err.get();
    }

    private static void assertStarted(FakeYoBleHarness harness, BleScanConfig config) {
        AtomicBoolean started = new AtomicBoolean(false);
        AtomicReference<BleFailure> err = new AtomicReference<>();
        harness.client.scan(config, new BleScanObserver() {
            @Override
            public void onScanStarted() {
                started.set(true);
            }

            @Override
            public void onScanFailed(BleFailure failure) {
                err.set(failure);
            }
        });
        harness.drain();
        assertTrue("Expected scan to start, err=" + err.get(), started.get());
    }
}
