package com.yovo.lib_bluetooth.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.yovo.lib_bluetooth.YoBluetoothSDK;
import com.yovo.lib_bluetooth.model.ble.BleAdvertiseConfig;
import com.yovo.lib_bluetooth.model.ble.BleAdvertiseData;
import com.yovo.lib_bluetooth.model.ble.BleAdvertiseObserver;
import com.yovo.lib_bluetooth.model.ble.BleBondObserver;
import com.yovo.lib_bluetooth.model.ble.BleCharacteristicScope;
import com.yovo.lib_bluetooth.model.ble.BleConnectionConfig;
import com.yovo.lib_bluetooth.model.ble.BleConnectionObserver;
import com.yovo.lib_bluetooth.model.ble.BleConnectionSnapshot;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleDeviceSnapshot;
import com.yovo.lib_bluetooth.model.ble.BleDiagnosticReport;
import com.yovo.lib_bluetooth.model.ble.BleErrorCode;
import com.yovo.lib_bluetooth.model.ble.BleFailure;
import com.yovo.lib_bluetooth.model.ble.BleGattCharacteristicInfo;
import com.yovo.lib_bluetooth.model.ble.BleGattDescriptorInfo;
import com.yovo.lib_bluetooth.model.ble.BleGattServerCharacteristic;
import com.yovo.lib_bluetooth.model.ble.BleGattServerConfig;
import com.yovo.lib_bluetooth.model.ble.BleGattServerObserver;
import com.yovo.lib_bluetooth.model.ble.BleGattServerService;
import com.yovo.lib_bluetooth.model.ble.BleOperationHandle;
import com.yovo.lib_bluetooth.model.ble.BleScanConfig;
import com.yovo.lib_bluetooth.model.ble.BleScanObserver;
import com.yovo.lib_bluetooth.model.ble.BleScanResult;
import com.yovo.lib_bluetooth.model.common.Result;
import com.yovo.lib_common.log.YoLog;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * BLE 公开门面 {@link YoBleClient} 的真机全链路仪器化验证测试（NEPI4E0106 / connectedDebugAndroidTest）。
 */
@RunWith(AndroidJUnit4.class)
public class YoBleClientInstrumentedTest {

    private static final String TAG = "YoBleInstrumentedTest";
    private static final UUID TEST_SERVICE_UUID =
            UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb");
    private static final UUID TEST_CHAR_UUID =
            UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb");

    private Context context;
    private YoBleClient client;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        grantBluetoothPermissions();
        client = YoBluetoothSDK.ble(context);
        YoLog.i(TAG, "BLE 客户端已创建，SDK " + Build.VERSION.SDK_INT
                + "，bleSupported=" + client.isBleSupported()
                + "，adapterEnabled=" + client.isAdapterEnabled());
    }

    @After
    public void tearDown() {
        if (client != null) {
            client.release();
            client = null;
        }
    }

    @Test
    public void capabilityAndAdapter_reportConsistentState() {
        boolean pmLe = context.getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
        boolean sdkLe = client.isBleSupported();
        YoLog.i(TAG, "System FEATURE_BLUETOOTH_LE=" + pmLe + ", client.isBleSupported=" + sdkLe);
        assertEquals(pmLe, sdkLe);
        // 确认 isAdapterEnabled 无异常且返回确定布尔值
        boolean adapterOn = client.isAdapterEnabled();
        YoLog.i(TAG, "client.isAdapterEnabled=" + adapterOn);
        Result<List<BleConnectionSnapshot>> connected = client.getConnectedDevices();
        assertTrue(connected.isSuccess());
        assertNotNull(connected.getValue());
    }

    @Test
    public void lifecycle_releaseAllowsFreshInstance() {
        client.release();
        // 重复 release 必须幂等
        client.release();

        // 已释放客户端应返回 RELEASED 错误而不是崩溃
        Result<List<BleDeviceSnapshot>> afterRelease = client.getBondedDevices();
        assertTrue(afterRelease.isFailure());
        assertEquals(BleErrorCode.RELEASED.name(), afterRelease.getFailure().code());

        YoBleClient second = YoBluetoothSDK.ble(context);
        assertNotSame(client, second);
        assertEquals(client.isBleSupported(), second.isBleSupported());
        second.release();
        client = null;
    }

    @Test
    public void scan_withPermissions_completesOrRespectsRadioState() throws Exception {
        AtomicBoolean started = new AtomicBoolean(false);
        AtomicInteger foundCount = new AtomicInteger(0);
        AtomicReference<BleFailure> failure = new AtomicReference<>();
        CountDownLatch finished = new CountDownLatch(1);

        BleScanConfig config = BleScanConfig.builder()
                .timeoutMillis(3_000L)
                .locationExempt(true)
                .build();

        BleOperationHandle handle = client.scan(config, new BleScanObserver() {
            @Override
            public void onScanStarted() {
                started.set(true);
                YoLog.i(TAG, "BLE 真实扫描已启动");
            }

            @Override
            public void onDeviceFound(@NonNull BleScanResult result) {
                int c = foundCount.incrementAndGet();
                if (c <= 5) {
                    YoLog.i(TAG, "BLE 扫描发现: " + result.identity().displayName()
                            + " [" + result.identity().deviceId() + "] rssi=" + result.rssi());
                }
            }

            @Override
            public void onScanStopped() {
                YoLog.i(TAG, "BLE 扫描已停止");
                finished.countDown();
            }

            @Override
            public void onScanFailed(@NonNull BleFailure f) {
                failure.set(f);
                YoLog.w(TAG, "BLE 扫描失败: " + f.errorCode() + " - " + f.message());
                finished.countDown();
            }
        });

        assertNotNull(handle);
        assertTrue("BLE scan should start/stop or report radio state cleanly",
                finished.await(10, TimeUnit.SECONDS));
        handle.cancel();
        assertFalse("Scanner must not remain active after completion", client.isScanning());

        if (client.isBleSupported() && client.isAdapterEnabled()) {
            if (failure.get() != null) {
                BleErrorCode code = failure.get().errorCode();
                assertFalse("Unexpected missing SCAN permission",
                        code == BleErrorCode.PERMISSION_SCAN_DENIED);
                YoLog.w(TAG, "环境扫描受限回调（已安全捕获）: " + code);
            } else {
                assertTrue("Scan should have started before timed stop", started.get());
            }
        } else {
            assertNotNull(failure.get());
        }
        YoLog.i(TAG, "扫描测试汇总: started=" + started.get()
                + ", found=" + foundCount.get()
                + ", snapshots=" + client.getDeviceSnapshots().getValue().size());
    }

    @Test
    public void stopScan_isIdempotentWhenIdle() {
        Result<Void> first = client.stopScan();
        Result<Void> second = client.stopScan();
        assertTrue(first.isSuccess());
        assertTrue(second.isSuccess());
        assertFalse(client.isScanning());
    }

    @Test
    public void invalidDeviceId_rejectedBeforeGatt() throws Exception {
        BleDeviceId invalidId = new BleDeviceId("INVALID_MAC");
        AtomicReference<BleFailure> failure = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        client.connect(invalidId, BleConnectionConfig.builder().build(), new BleConnectionObserver() {
            @Override
            public void onConnectionFailed(@NonNull BleFailure f) {
                failure.set(f);
                latch.countDown();
            }
        });

        assertTrue("Invalid MAC connect must reject asynchronously", latch.await(3, TimeUnit.SECONDS));
        assertNotNull(failure.get());
        assertEquals(BleErrorCode.DEVICE_INVALID, failure.get().errorCode());
        assertTrue(client.getConnectionSnapshot(invalidId).isFailure());
        assertEquals(BleErrorCode.DEVICE_INVALID.name(),
                client.getConnectionSnapshot(invalidId).getFailure().code());
    }

    @Test
    public void bondedDevices_returnsListWithoutCrash() {
        Result<List<BleDeviceSnapshot>> result = client.getBondedDevices();
        YoLog.i(TAG, "getBondedDevices success=" + result.isSuccess());
        assertTrue("getBondedDevices should succeed with BLUETOOTH_CONNECT", result.isSuccess());
        assertNotNull(result.getValue());
        for (BleDeviceSnapshot snap : result.getValue()) {
            YoLog.i(TAG, "已绑定 BLE/Dual 设备: " + snap.identity().displayName()
                    + " [" + snap.deviceId() + "] bond=" + snap.bondState());
        }
    }

    @Test
    public void advertising_bestEffortWithPermission() throws Exception {
        AtomicBoolean started = new AtomicBoolean(false);
        AtomicBoolean stopped = new AtomicBoolean(false);
        AtomicReference<BleFailure> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        BleAdvertiseConfig config = BleAdvertiseConfig.builder()
                .legacyMode(true)
                .connectable(true)
                .scannable(true)
                .timeoutMillis(1_500)
                .build();
        BleAdvertiseData data = BleAdvertiseData.builder()
                .addServiceUuid(TEST_SERVICE_UUID)
                .build();

        BleOperationHandle handle = client.startAdvertising(config, data, null, new BleAdvertiseObserver() {
            @Override
            public void onAdvertisingStarted() {
                started.set(true);
                YoLog.i(TAG, "BLE 广播已启动（真实芯片）");
            }

            @Override
            public void onAdvertisingStopped() {
                stopped.set(true);
                YoLog.i(TAG, "BLE 广播已停止");
                done.countDown();
            }

            @Override
            public void onAdvertisingFailed(@NonNull BleFailure f) {
                failure.set(f);
                YoLog.w(TAG, "BLE 广播失败（机型/无线电策略受限）: " + f.errorCode() + " - " + f.message());
                done.countDown();
            }
        });

        assertNotNull(handle);
        assertTrue("Advertising lifecycle must terminate within timeout",
                done.await(6, TimeUnit.SECONDS));
        assertTrue(client.stopAdvertising().isSuccess());
        assertTrue(client.stopAdvertising().isSuccess());

        if (failure.get() != null) {
            assertFalse("BLUETOOTH_ADVERTISE must not be missing",
                    failure.get().errorCode() == BleErrorCode.PERMISSION_ADVERTISE_DENIED);
        } else {
            assertTrue(started.get());
            assertTrue(stopped.get());
        }
    }

    @Test
    public void gattServer_andDiagnostics_workEndToEndOnDevice() throws Exception {
        CountDownLatch serverStartedOrFailed = new CountDownLatch(1);
        AtomicBoolean serverStarted = new AtomicBoolean(false);
        AtomicBoolean serverStopped = new AtomicBoolean(false);
        AtomicReference<BleFailure> serverFailure = new AtomicReference<>();

        BleGattServerConfig serverConfig = BleGattServerConfig.builder()
                .addService(BleGattServerService.builder(TEST_SERVICE_UUID)
                        .addCharacteristic(BleGattServerCharacteristic.builder(
                                        TEST_CHAR_UUID,
                                        BleGattCharacteristicInfo.PROPERTY_READ
                                                | BleGattCharacteristicInfo.PROPERTY_WRITE
                                                | BleGattCharacteristicInfo.PROPERTY_NOTIFY,
                                        BleGattDescriptorInfo.PERMISSION_READ
                                                | BleGattDescriptorInfo.PERMISSION_WRITE)
                                .scope(BleCharacteristicScope.PER_CLIENT)
                                .initialValue(new byte[]{0x01, 0x02})
                                .withCccd()
                                .build())
                        .build())
                .build();

        BleOperationHandle serverHandle = client.startGattServer(serverConfig, new BleGattServerObserver() {
            @Override
            public void onServerStarted() {
                serverStarted.set(true);
                YoLog.i(TAG, "本地 GATT Server 已启动");
                serverStartedOrFailed.countDown();
            }

            @Override
            public void onServerStopped() {
                serverStopped.set(true);
                YoLog.i(TAG, "本地 GATT Server 已停止");
            }

            @Override
            public void onFailed(@NonNull BleFailure f) {
                serverFailure.set(f);
                YoLog.w(TAG, "GATT Server 启动失败: " + f.errorCode() + " - " + f.message());
                serverStartedOrFailed.countDown();
            }
        });

        assertNotNull(serverHandle);
        assertTrue("GATT Server start must settle promptly",
                serverStartedOrFailed.await(5, TimeUnit.SECONDS));

        if (client.isBleSupported() && client.isAdapterEnabled()) {
            if (serverFailure.get() != null) {
                assertFalse(serverFailure.get().errorCode() == BleErrorCode.PERMISSION_CONNECT_DENIED);
            } else {
                assertTrue(serverStarted.get());
            }
        }

        assertTrue(client.stopGattServer().isSuccess());
        assertTrue(client.stopGattServer().isSuccess());

        BleOperationHandle connObs = client.observeConnectionState(new BleConnectionObserver() {});
        BleOperationHandle bondObs = client.observeBondState(
                (BleBondObserver) (deviceId, previousState, currentState, rawReason) -> {});
        connObs.cancel();
        bondObs.cancel();

        Result<BleDiagnosticReport> reportRes = client.exportDiagnosticReport();
        assertTrue(reportRes.isSuccess());
        BleDiagnosticReport report = reportRes.getValue();
        assertNotNull(report);
        assertTrue(report.dumpText().contains("=== YoBleClient Diagnostic Dump ==="));
        YoLog.i(TAG, "诊断报告事件数=" + report.recentTimelineEntries().size()
                + ", serverStopped=" + serverStopped.get());
    }

    private static void grantBluetoothPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return;
        }
        try {
            var ui = InstrumentationRegistry.getInstrumentation().getUiAutomation();
            String pkg = InstrumentationRegistry.getInstrumentation()
                    .getTargetContext().getPackageName();
            ui.grantRuntimePermission(pkg, Manifest.permission.BLUETOOTH_SCAN);
            ui.grantRuntimePermission(pkg, Manifest.permission.BLUETOOTH_CONNECT);
            ui.grantRuntimePermission(pkg, Manifest.permission.BLUETOOTH_ADVERTISE);
            ui.grantRuntimePermission(pkg, Manifest.permission.ACCESS_FINE_LOCATION);
            YoLog.i(TAG, "已授予 SCAN/CONNECT/ADVERTISE/LOCATION 权限给 " + pkg);
        } catch (Throwable t) {
            YoLog.w(TAG, "通过 UiAutomation 授予权限失败", t);
        }
    }
}
