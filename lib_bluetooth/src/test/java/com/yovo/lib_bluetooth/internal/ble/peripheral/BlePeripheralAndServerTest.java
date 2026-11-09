package com.yovo.lib_bluetooth.internal.ble.peripheral;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_bluetooth.internal.ble.error.YoBleGattStatus;
import com.yovo.lib_bluetooth.internal.ble.testing.FakeYoBleHarness;
import com.yovo.lib_bluetooth.internal.ble.testing.FakeYoBlePeripheralPlatform;
import com.yovo.lib_bluetooth.internal.common.permission.YoBluetoothPermission;
import com.yovo.lib_bluetooth.model.ble.BleAdvertiseConfig;
import com.yovo.lib_bluetooth.model.ble.BleAdvertiseData;
import com.yovo.lib_bluetooth.model.ble.BleAdvertiseObserver;
import com.yovo.lib_bluetooth.model.ble.BleCharacteristicScope;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleErrorCode;
import com.yovo.lib_bluetooth.model.ble.BleFailure;
import com.yovo.lib_bluetooth.model.ble.BleGattCharacteristicInfo;
import com.yovo.lib_bluetooth.model.ble.BleGattDescriptorInfo;
import com.yovo.lib_bluetooth.model.ble.BleGattServerCharacteristic;
import com.yovo.lib_bluetooth.model.ble.BleGattServerConfig;
import com.yovo.lib_bluetooth.model.ble.BleGattServerObserver;
import com.yovo.lib_bluetooth.model.ble.BleGattServerService;
import com.yovo.lib_bluetooth.model.ble.BleGattTarget;
import com.yovo.lib_bluetooth.model.ble.BleOperationHandle;
import com.yovo.lib_bluetooth.model.common.Result;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * BLE-025 BLE 外设广播（Legacy/Extended + 权限前检）与多客户端 GATT Server（SHARED/PER_CLIENT、CCCD、PreparedWrite）验证。
 */
public class BlePeripheralAndServerTest {

    private static final UUID SVC_UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb");
    private static final UUID SHARED_CHAR = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb");
    private static final UUID CLIENT_CHAR = UUID.fromString("00002a38-0000-1000-8000-00805f9b34fb");

    @Test
    public void advertising_permissionApiCheck_legacyTimeoutAndExtendedCancel() {
        FakeYoBleHarness harness = new FakeYoBleHarness();
        try {
            BleAdvertiseData advData = BleAdvertiseData.builder()
                    .addServiceUuid(SVC_UUID)
                    .includeDeviceName(true)
                    .build();

            // 1. 缺少 ADVERTISE 权限返回 PERMISSION_ADVERTISE_DENIED
            harness.env.deny(YoBluetoothPermission.ADVERTISE);
            List<BleFailure> advFailures = new ArrayList<>();
            harness.client.startAdvertising(
                    BleAdvertiseConfig.builder().legacyMode(true).build(),
                    advData, null,
                    new BleAdvertiseObserver() {
                        @Override
                        public void onAdvertisingFailed(BleFailure failure) {
                            advFailures.add(failure);
                        }
                    });
            harness.drain();
            assertEquals(1, advFailures.size());
            assertEquals(BleErrorCode.PERMISSION_ADVERTISE_DENIED, advFailures.get(0).errorCode());
            assertFalse(harness.peripheralPlatform.isAdvertisingActive());

            // 2. API < 26 不支持 Extended Advertising (legacyMode=false)
            harness.env.grantAll();
            harness.env.setSdkInt(25);
            advFailures.clear();
            harness.client.startAdvertising(
                    BleAdvertiseConfig.builder().legacyMode(false).build(),
                    advData, null,
                    new BleAdvertiseObserver() {
                        @Override
                        public void onAdvertisingFailed(BleFailure failure) {
                            advFailures.add(failure);
                        }
                    });
            harness.drain();
            assertEquals(BleErrorCode.UNSUPPORTED_OPERATION, advFailures.get(0).errorCode());

            // 3. Legacy 模式超时自动停止
            AtomicBoolean started = new AtomicBoolean(false);
            AtomicBoolean stopped = new AtomicBoolean(false);
            harness.client.startAdvertising(
                    BleAdvertiseConfig.builder().legacyMode(true).timeoutMillis(500).build(),
                    advData, null,
                    new BleAdvertiseObserver() {
                        @Override
                        public void onAdvertisingStarted() {
                            started.set(true);
                        }

                        @Override
                        public void onAdvertisingStopped() {
                            stopped.set(true);
                        }
                    });
            harness.drain();
            assertTrue(started.get());
            assertTrue(harness.peripheralPlatform.isAdvertisingActive());

            harness.advanceMillis(500L);
            assertTrue(stopped.get());
            assertFalse(harness.peripheralPlatform.isAdvertisingActive());

            // 4. API 29 Extended 模式启停与句柄 cancel 幂等
            harness.env.setSdkInt(29);
            AtomicBoolean extStarted = new AtomicBoolean(false);
            AtomicBoolean extStopped = new AtomicBoolean(false);
            BleOperationHandle handle = harness.client.startAdvertising(
                    BleAdvertiseConfig.builder().legacyMode(false).build(),
                    advData, null,
                    new BleAdvertiseObserver() {
                        @Override
                        public void onAdvertisingStarted() {
                            extStarted.set(true);
                        }

                        @Override
                        public void onAdvertisingStopped() {
                            extStopped.set(true);
                        }
                    });
            harness.drain();
            assertTrue(extStarted.get());
            handle.cancel();
            harness.client.stopAdvertising();
            harness.drain();
            assertTrue(extStopped.get());
            assertFalse(harness.peripheralPlatform.isAdvertisingActive());
        } finally {
            harness.release();
        }
    }

    @Test
    public void gattServer_sharedVsPerClientIsolation_cccdNotify_andPreparedWriteCommitVsAbort() {
        FakeYoBleHarness harness = new FakeYoBleHarness();
        BleDeviceId clientA = new BleDeviceId("AA:BB:CC:DD:EE:81");
        BleDeviceId clientB = new BleDeviceId("AA:BB:CC:DD:EE:82");
        BleGattTarget sharedTarget = BleGattTarget.characteristic(SVC_UUID, SHARED_CHAR);
        BleGattTarget perClientTarget = BleGattTarget.characteristic(SVC_UUID, CLIENT_CHAR);
        BleGattTarget cccdTarget = BleGattTarget.descriptor(
                SVC_UUID, CLIENT_CHAR, com.yovo.lib_bluetooth.model.ble.BleGattDatabaseSnapshot.CCCD_UUID);
        try {
            BleGattServerConfig serverConfig = BleGattServerConfig.builder()
                    .addService(BleGattServerService.builder(SVC_UUID)
                            .addCharacteristic(BleGattServerCharacteristic.builder(
                                            SHARED_CHAR,
                                            BleGattCharacteristicInfo.PROPERTY_READ | BleGattCharacteristicInfo.PROPERTY_WRITE,
                                            BleGattDescriptorInfo.PERMISSION_READ | BleGattDescriptorInfo.PERMISSION_WRITE)
                                    .scope(BleCharacteristicScope.SHARED)
                                    .initialValue(new byte[]{0x01})
                                    .build())
                            .addCharacteristic(BleGattServerCharacteristic.builder(
                                            CLIENT_CHAR,
                                            BleGattCharacteristicInfo.PROPERTY_READ
                                                    | BleGattCharacteristicInfo.PROPERTY_WRITE
                                                    | BleGattCharacteristicInfo.PROPERTY_NOTIFY,
                                            BleGattDescriptorInfo.PERMISSION_READ | BleGattDescriptorInfo.PERMISSION_WRITE)
                                    .scope(BleCharacteristicScope.PER_CLIENT)
                                    .initialValue(new byte[]{0x00})
                                    .withCccd()
                                    .build())
                            .build())
                    .build();

            // 1. 缺少 CONNECT 拒绝启动 GATT Server
            harness.env.deny(YoBluetoothPermission.CONNECT);
            AtomicReference<BleFailure> serverErr = new AtomicReference<>();
            harness.client.startGattServer(serverConfig, new BleGattServerObserver() {
                @Override
                public void onFailed(BleFailure failure) {
                    serverErr.set(failure);
                }
            });
            harness.drain();
            assertEquals(BleErrorCode.PERMISSION_CONNECT_DENIED, serverErr.get().errorCode());

            // 2. 授权启动 GATT Server
            harness.env.grantAll();
            AtomicBoolean serverStarted = new AtomicBoolean(false);
            List<String> events = new ArrayList<>();
            List<byte[]> committedWrites = new ArrayList<>();
            harness.client.startGattServer(serverConfig, new BleGattServerObserver() {
                @Override
                public void onServerStarted() {
                    serverStarted.set(true);
                }

                @Override
                public void onClientConnected(BleDeviceId id) {
                    events.add("conn:" + id);
                }

                @Override
                public void onClientWriteCharacteristic(BleDeviceId id,
                                                        BleGattTarget target,
                                                        byte[] value,
                                                        boolean withResponse) {
                    committedWrites.add(value);
                }

                @Override
                public void onClientSubscriptionChanged(BleDeviceId id,
                                                        BleGattTarget target,
                                                        boolean notifyEnabled,
                                                        boolean indicateEnabled) {
                    events.add("sub:" + id + ":" + notifyEnabled + ":" + indicateEnabled);
                }
            });
            harness.drain();
            assertTrue(serverStarted.get());

            YoBlePeripheralPlatform.GattServerPlatformCallback cb =
                    harness.peripheralPlatform.serverCallback();
            cb.onConnectionStateChange(clientA, true);
            cb.onConnectionStateChange(clientB, true);
            harness.drain();

            // 3. PER_CLIENT 特征隔离验证：A 与 B 各自写互不覆盖
            cb.onCharacteristicWriteRequest(clientA, 1, perClientTarget, false, true, 0, new byte[]{0x11, 0x22});
            cb.onCharacteristicWriteRequest(clientB, 2, perClientTarget, false, true, 0, new byte[]{0x33, 0x44});
            cb.onCharacteristicReadRequest(clientA, 3, 0, perClientTarget);
            cb.onCharacteristicReadRequest(clientB, 4, 0, perClientTarget);
            harness.drain();

            FakeYoBlePeripheralPlatform.SentResponse respReadA = findResponseByReqId(
                    harness.peripheralPlatform.sentResponses(), 3);
            FakeYoBlePeripheralPlatform.SentResponse respReadB = findResponseByReqId(
                    harness.peripheralPlatform.sentResponses(), 4);
            assertArrayEquals(new byte[]{0x11, 0x22}, respReadA.value);
            assertArrayEquals(new byte[]{0x33, 0x44}, respReadB.value);

            // 4. SHARED 特征跨客户端可见
            cb.onCharacteristicWriteRequest(clientA, 5, sharedTarget, false, true, 0, new byte[]{0x7E});
            cb.onCharacteristicReadRequest(clientB, 6, 0, sharedTarget);
            harness.drain();
            assertArrayEquals(new byte[]{0x7E}, findResponseByReqId(
                    harness.peripheralPlatform.sentResponses(), 6).value);

            // 5. CCCD 订阅识别 + 定向 Notify 下发
            cb.onDescriptorWriteRequest(clientA, 7, cccdTarget, false, true, 0, new byte[]{0x01, 0x00});
            harness.drain();
            assertTrue(events.contains("sub:" + clientA + ":true:false"));

            Result<Void> notifyRes = harness.client.notifyServerCharacteristic(
                    clientA, perClientTarget, false, new byte[]{0x5A});
            assertTrue(notifyRes.isSuccess());
            assertEquals(1, harness.peripheralPlatform.sentNotifications().size());
            assertEquals(clientA, harness.peripheralPlatform.sentNotifications().get(0).clientDeviceId);
            assertArrayEquals(new byte[]{0x5A}, harness.peripheralPlatform.sentNotifications().get(0).value);

            // 6. PreparedWrite 分块写入 -> 提交前不可见 -> execute=true 后合并落库
            committedWrites.clear();
            cb.onCharacteristicWriteRequest(clientA, 8, perClientTarget, true, true, 0, new byte[]{(byte) 0xAA, (byte) 0xBB});
            cb.onCharacteristicWriteRequest(clientA, 9, perClientTarget, true, true, 2, new byte[]{(byte) 0xCC, (byte) 0xDD});
            cb.onCharacteristicReadRequest(clientA, 10, 0, perClientTarget);
            harness.drain();
            assertArrayEquals(new byte[]{0x11, 0x22}, findResponseByReqId(
                    harness.peripheralPlatform.sentResponses(), 10).value);

            cb.onExecuteWrite(clientA, 11, true);
            cb.onCharacteristicReadRequest(clientA, 12, 0, perClientTarget);
            harness.drain();
            assertArrayEquals(new byte[]{(byte) 0xAA, (byte) 0xBB, (byte) 0xCC, (byte) 0xDD},
                    findResponseByReqId(harness.peripheralPlatform.sentResponses(), 12).value);
            assertEquals(2, committedWrites.size());

            // 7. PreparedWrite -> execute=false 中止丢弃，保持原值不被污染
            cb.onCharacteristicWriteRequest(clientA, 13, perClientTarget, true, true, 0, new byte[]{0x00, 0x00});
            cb.onExecuteWrite(clientA, 14, false);
            cb.onCharacteristicReadRequest(clientA, 15, 0, perClientTarget);
            harness.drain();
            FakeYoBlePeripheralPlatform.SentResponse afterAbort = findResponseByReqId(
                    harness.peripheralPlatform.sentResponses(), 15);
            assertEquals(YoBleGattStatus.GATT_SUCCESS, afterAbort.status);
            assertArrayEquals(new byte[]{(byte) 0xAA, (byte) 0xBB, (byte) 0xCC, (byte) 0xDD}, afterAbort.value);

            assertTrue(harness.client.stopGattServer().isSuccess());
            harness.drain();
            assertFalse(harness.peripheralPlatform.isServerActive());
        } finally {
            harness.release();
        }
    }

    @Test
    public void cccdOnSharedCharacteristic_isPerClientIsolated_andStaleHandleCancelIgnored_andError5Mapped() {
        FakeYoBleHarness harness = new FakeYoBleHarness();
        BleDeviceId clientA = new BleDeviceId("AA:BB:CC:DD:EE:83");
        BleDeviceId clientB = new BleDeviceId("AA:BB:CC:DD:EE:84");
        BleGattTarget sharedCccd = BleGattTarget.descriptor(
                SVC_UUID, SHARED_CHAR, com.yovo.lib_bluetooth.model.ble.BleGattDatabaseSnapshot.CCCD_UUID);
        try {
            BleGattServerConfig cfg = BleGattServerConfig.builder()
                    .addService(BleGattServerService.builder(SVC_UUID)
                            .addCharacteristic(BleGattServerCharacteristic.builder(
                                            SHARED_CHAR,
                                            BleGattCharacteristicInfo.PROPERTY_READ
                                                    | BleGattCharacteristicInfo.PROPERTY_NOTIFY,
                                            BleGattDescriptorInfo.PERMISSION_READ)
                                    .scope(BleCharacteristicScope.SHARED)
                                    .withCccd()
                                    .build())
                            .build())
                    .build();

            // 1. SHARED 特征挂接 CCCD (0x2902) 仍须严格按客户端隔离
            harness.client.startGattServer(cfg, new BleGattServerObserver() {});
            harness.drain();
            YoBlePeripheralPlatform.GattServerPlatformCallback cb =
                    harness.peripheralPlatform.serverCallback();
            cb.onConnectionStateChange(clientA, true);
            cb.onConnectionStateChange(clientB, true);
            harness.drain();

            cb.onDescriptorWriteRequest(clientA, 101, sharedCccd, false, true, 0, new byte[]{0x01, 0x00});
            cb.onDescriptorReadRequest(clientA, 102, 0, sharedCccd);
            cb.onDescriptorReadRequest(clientB, 103, 0, sharedCccd);
            harness.drain();

            assertArrayEquals(new byte[]{0x01, 0x00}, findResponseByReqId(
                    harness.peripheralPlatform.sentResponses(), 102).value);
            assertArrayEquals(new byte[]{0x00, 0x00}, findResponseByReqId(
                    harness.peripheralPlatform.sentResponses(), 103).value);

            // 2. Stale startAdvertising / startGattServer handle cancel() 不得关闭新一代实例
            BleAdvertiseData advData = BleAdvertiseData.builder().addServiceUuid(SVC_UUID).build();
            BleOperationHandle oldAdv = harness.client.startAdvertising(
                    BleAdvertiseConfig.builder().legacyMode(true).build(),
                    advData, null, new BleAdvertiseObserver() {});
            BleOperationHandle newAdv = harness.client.startAdvertising(
                    BleAdvertiseConfig.builder().legacyMode(true).build(),
                    advData, null, new BleAdvertiseObserver() {});
            harness.drain();
            assertTrue(harness.peripheralPlatform.isAdvertisingActive());
            oldAdv.cancel();
            harness.drain();
            assertTrue("Stale advertising handle must not stop active generation",
                    harness.peripheralPlatform.isAdvertisingActive());
            newAdv.cancel();
            harness.drain();
            assertFalse(harness.peripheralPlatform.isAdvertisingActive());

            BleOperationHandle oldServer = harness.client.startGattServer(cfg, new BleGattServerObserver() {});
            BleOperationHandle newServer = harness.client.startGattServer(cfg, new BleGattServerObserver() {});
            harness.drain();
            assertTrue(harness.peripheralPlatform.isServerActive());
            oldServer.cancel();
            harness.drain();
            assertTrue("Stale server handle must not stop active server generation",
                    harness.peripheralPlatform.isServerActive());
            newServer.cancel();
            harness.drain();
            assertFalse(harness.peripheralPlatform.isServerActive());

            // 3. ADVERTISE_FAILED_FEATURE_UNSUPPORTED (5) -> BleErrorCode.UNSUPPORTED_OPERATION
            harness.peripheralPlatform.setNextAdvertiseAsyncError(5);
            AtomicReference<BleFailure> advErr = new AtomicReference<>();
            harness.client.startAdvertising(
                    BleAdvertiseConfig.builder().legacyMode(true).build(),
                    advData, null, new BleAdvertiseObserver() {
                        @Override
                        public void onAdvertisingFailed(BleFailure failure) {
                            advErr.set(failure);
                        }
                    });
            harness.drain();
            assertEquals(BleErrorCode.UNSUPPORTED_OPERATION, advErr.get().errorCode());
        } finally {
            harness.release();
        }
    }

    private static FakeYoBlePeripheralPlatform.SentResponse findResponseByReqId(
            List<FakeYoBlePeripheralPlatform.SentResponse> responses, int reqId) {
        for (FakeYoBlePeripheralPlatform.SentResponse r : responses) {
            if (r.requestId == reqId) {
                return r;
            }
        }
        throw new AssertionError("Missing SentResponse for requestId=" + reqId);
    }

    @Test
    public void advertiseCompatAndAndroidPeripheralContract_timeoutClampPhyAndCanonicalFallbackPresent()
            throws IOException {
        File moduleRoot = new File("src/main/java").isDirectory() ? new File(".") : new File("lib_bluetooth");
        String advCompat = Files.readString(new File(moduleRoot,
                "src/main/java/com/yovo/lib_bluetooth/internal/ble/internal/android/compat/YoBleAdvertiseCompat.java")
                .toPath(), StandardCharsets.UTF_8);
        String androidPeripheral = Files.readString(new File(moduleRoot,
                "src/main/java/com/yovo/lib_bluetooth/internal/ble/internal/android/YoBleAndroidPeripheralPlatform.java")
                .toPath(), StandardCharsets.UTF_8);

        assertTrue(advCompat.contains("Math.min(180_000, config.timeoutMillis())"));
        assertTrue(advCompat.contains("if (!config.legacyMode())"));
        assertTrue(advCompat.contains("config.connectable() || config.scannable()"));
        assertTrue(androidPeripheral.contains("BleGattTarget.characteristic(target.serviceUuid(), target.characteristicUuid())"));
        assertTrue(androidPeripheral.contains("isAdvertisingSetSuccess"));
    }
}
