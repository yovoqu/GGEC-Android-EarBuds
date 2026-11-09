package com.yovo.lib_bluetooth.internal.ble.gatt;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.yovo.lib_bluetooth.internal.ble.testing.FakeBleGattDevice;
import com.yovo.lib_bluetooth.internal.ble.testing.FakeYoBleHarness;
import com.yovo.lib_bluetooth.model.ble.BleBondState;
import com.yovo.lib_bluetooth.model.ble.BleConfig;
import com.yovo.lib_bluetooth.model.ble.BleConnectionConfig;
import com.yovo.lib_bluetooth.model.ble.BleConnectionObserver;
import com.yovo.lib_bluetooth.model.ble.BleConnectionSnapshot;
import com.yovo.lib_bluetooth.model.ble.BleConnectionState;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleErrorCode;
import com.yovo.lib_bluetooth.model.ble.BleFailure;
import com.yovo.lib_bluetooth.model.ble.BleGattCharacteristicInfo;
import com.yovo.lib_bluetooth.model.ble.BleGattDatabaseSnapshot;
import com.yovo.lib_bluetooth.model.ble.BleGattDescriptorInfo;
import com.yovo.lib_bluetooth.model.ble.BleGattObserver;
import com.yovo.lib_bluetooth.model.ble.BleGattServiceInfo;
import com.yovo.lib_bluetooth.model.ble.BleGattTarget;
import com.yovo.lib_bluetooth.model.ble.BleOperationHandle;
import com.yovo.lib_bluetooth.model.ble.BleValueObserver;
import com.yovo.lib_bluetooth.model.common.Result;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * BLE-015 GATT 服务发现树不可变快照 / 同 UUID 多实例 instanceId 精准寻址 / 已配对延迟 / 受控缓存刷新测试。
 */
public class BleServiceDiscoveryAndCacheTest {

    private static final UUID DUP_SVC_UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");
    private static final UUID DUP_CHR_UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");
    private static final UUID DESC_2901 = UUID.fromString("00002901-0000-1000-8000-00805f9b34fb");

    @Test
    public void buildSnapshotHierarchy_andDisambiguateDuplicateUuidsByInstanceId() {
        FakeYoBleHarness harness = new FakeYoBleHarness();
        BleDeviceId id = new BleDeviceId("AA:BB:CC:DD:EE:15");
        try {
            FakeBleGattDevice dev = harness.connectionPlatform.device(id);

            BleGattDescriptorInfo desc = new BleGattDescriptorInfo(
                    DESC_2901, BleGattDescriptorInfo.PERMISSION_READ);
            BleGattCharacteristicInfo c1 = new BleGattCharacteristicInfo(
                    DUP_CHR_UUID, 101,
                    BleGattCharacteristicInfo.PROPERTY_READ, 0, Collections.singletonList(desc));
            BleGattCharacteristicInfo c2 = new BleGattCharacteristicInfo(
                    DUP_CHR_UUID, 102,
                    BleGattCharacteristicInfo.PROPERTY_READ, 0, Collections.emptyList());
            BleGattCharacteristicInfo c3 = new BleGattCharacteristicInfo(
                    DUP_CHR_UUID, 201,
                    BleGattCharacteristicInfo.PROPERTY_READ, 0, Collections.emptyList());

            BleGattServiceInfo svc10 = new BleGattServiceInfo(
                    DUP_SVC_UUID, 10, true, Arrays.asList(c1, c2), Collections.emptyList());
            BleGattServiceInfo svc20 = new BleGattServiceInfo(
                    DUP_SVC_UUID, 20, true, Collections.singletonList(c3), Collections.emptyList());

            BleGattTarget t102 = BleGattTarget.characteristic(DUP_SVC_UUID, 10, DUP_CHR_UUID, 102);
            BleGattTarget t201 = BleGattTarget.characteristic(DUP_SVC_UUID, 20, DUP_CHR_UUID, 201);

            dev.setServices(Arrays.asList(svc10, svc20))
                    .putValue(t102, new byte[]{0x12})
                    .putValue(t201, new byte[]{0x34});

            harness.client.connect(id, BleConnectionConfig.builder().requestMtu(null).build(),
                    new BleConnectionObserver() {});
            harness.drain();

            Result<BleGattDatabaseSnapshot> res = harness.client.getServices(id);
            assertTrue(res.isSuccess());
            BleGattDatabaseSnapshot db = res.getValue();
            assertNotNull(db);
            assertEquals(2, db.services().size());

            try {
                db.services().add(svc10);
                fail("BleGattDatabaseSnapshot.services() must be unmodifiable");
            } catch (UnsupportedOperationException expected) {
            }

            assertEquals(101, db.findCharacteristic(
                    BleGattTarget.characteristic(DUP_SVC_UUID, 10, DUP_CHR_UUID, 101)).instanceId());
            assertNotNull(db.findDescriptor(
                    BleGattTarget.descriptor(DUP_SVC_UUID, 10, DUP_CHR_UUID, 101, DESC_2901)));

            // 同 UUID 不同 instanceId 各自读出精准对应值
            AtomicReference<byte[]> val102 = new AtomicReference<>();
            AtomicReference<byte[]> val201 = new AtomicReference<>();
            harness.client.read(id, t102, new BleGattObserver() {
                @Override
                public void onRead(BleDeviceId d, BleGattTarget t, byte[] value) {
                    val102.set(value);
                }
            });
            harness.client.read(id, t201, new BleGattObserver() {
                @Override
                public void onRead(BleDeviceId d, BleGattTarget t, byte[] value) {
                    val201.set(value);
                }
            });
            harness.drain();

            assertArrayEquals(new byte[]{0x12}, val102.get());
            assertArrayEquals(new byte[]{0x34}, val201.get());
        } finally {
            harness.release();
        }
    }

    @Test
    public void bondedDevice_delaysServiceDiscoveryByConfigMillis() {
        FakeYoBleHarness harness = new FakeYoBleHarness();
        BleDeviceId id = new BleDeviceId("AA:BB:CC:DD:EE:16");
        try {
            harness.bondPlatform.setBondState(id, BleBondState.BONDED);
            FakeBleGattDevice dev = harness.connectionPlatform.device(id);

            List<BleConnectionState> states = new ArrayList<>();
            harness.client.connect(id, BleConnectionConfig.builder()
                    .bondedDiscoveryDelayMillis(600L)
                    .requestMtu(null)
                    .build(), new BleConnectionObserver() {
                @Override
                public void onConnectionStateChanged(BleConnectionSnapshot snapshot) {
                    states.add(snapshot.state());
                }
            });
            harness.drain();

            assertEquals(0, dev.discoverCalls());
            assertTrue(states.contains(BleConnectionState.DISCOVERING_SERVICES));

            harness.advanceMillis(599L);
            assertEquals(0, dev.discoverCalls());

            harness.advanceMillis(1L);
            assertEquals(1, dev.discoverCalls());
            assertEquals(BleConnectionState.READY, states.get(states.size() - 1));
        } finally {
            harness.release();
        }
    }

    @Test
    public void refreshDeviceCache_enforcesHiddenApiPolicy_andForceRefreshRediscovers() {
        BleDeviceId id = new BleDeviceId("AA:BB:CC:DD:EE:17");

        // 策略默认关闭：拒绝反射刷新缓存且不崩溃
        FakeYoBleHarness disabledHarness = new FakeYoBleHarness(
                BleConfig.builder().allowHiddenApiCacheRefresh(false).build());
        try {
            disabledHarness.client.connect(id, BleConnectionConfig.builder().requestMtu(null).build(),
                    new BleConnectionObserver() {});
            disabledHarness.drain();

            Result<Void> blocked = disabledHarness.client.refreshDeviceCache(id);
            assertTrue(blocked.isFailure());
            assertEquals(BleErrorCode.HIDDEN_API_BLOCKED.name(), blocked.getFailure().code());
            assertEquals(0, disabledHarness.connectionPlatform.device(id).refreshCacheCalls());

            AtomicReference<BleFailure> forceErr = new AtomicReference<>();
            disabledHarness.client.discoverServices(id, true, new BleValueObserver<BleGattDatabaseSnapshot>() {
                @Override
                public void onSuccess(BleGattDatabaseSnapshot value) {
                }

                @Override
                public void onFailed(BleFailure failure) {
                    forceErr.set(failure);
                }
            });
            disabledHarness.drain();
            assertNotNull(forceErr.get());
            assertEquals(BleErrorCode.HIDDEN_API_BLOCKED, forceErr.get().errorCode());
        } finally {
            disabledHarness.release();
        }

        // 显式授权：放行刷新并支持 forceRefresh
        FakeYoBleHarness allowedHarness = new FakeYoBleHarness(
                BleConfig.builder().allowHiddenApiCacheRefresh(true).build());
        try {
            FakeBleGattDevice dev = allowedHarness.connectionPlatform.device(id);
            allowedHarness.client.connect(id, BleConnectionConfig.builder().requestMtu(null).build(),
                    new BleConnectionObserver() {});
            allowedHarness.drain();

            Result<Void> ok = allowedHarness.client.refreshDeviceCache(id);
            assertTrue(ok.isSuccess());
            assertEquals(1, dev.refreshCacheCalls());

            AtomicReference<BleGattDatabaseSnapshot> rediscovered = new AtomicReference<>();
            allowedHarness.client.discoverServices(id, true, new BleValueObserver<BleGattDatabaseSnapshot>() {
                @Override
                public void onSuccess(BleGattDatabaseSnapshot value) {
                    rediscovered.set(value);
                }
            });
            allowedHarness.drain();
            assertNotNull(rediscovered.get());
            assertEquals(2, dev.refreshCacheCalls());
            assertEquals(2, dev.discoverCalls());

            dev.setRefreshCacheResult(false);
            Result<Void> failRes = allowedHarness.client.refreshDeviceCache(id);
            assertTrue(failRes.isFailure());
            assertEquals(BleErrorCode.HIDDEN_API_FAILED.name(), failRes.getFailure().code());
        } finally {
            allowedHarness.release();
        }
    }

    @Test
    public void emptyServices_andDiscoverReturningFalse_handledSafely() {
        FakeYoBleHarness harness = new FakeYoBleHarness();
        BleDeviceId id1 = new BleDeviceId("AA:BB:CC:DD:EE:18");
        BleDeviceId id2 = new BleDeviceId("AA:BB:CC:DD:EE:19");
        try {
            harness.connectionPlatform.device(id1).setServices(Collections.emptyList());
            List<BleConnectionState> s1 = new ArrayList<>();
            harness.client.connect(id1, BleConnectionConfig.builder().requestMtu(null).build(),
                    new BleConnectionObserver() {
                        @Override
                        public void onConnectionStateChanged(BleConnectionSnapshot snapshot) {
                            s1.add(snapshot.state());
                        }
                    });
            harness.drain();
            assertTrue(harness.client.getServices(id1).getValue().services().isEmpty());
            assertEquals(BleConnectionState.READY, s1.get(s1.size() - 1));

            harness.connectionPlatform.device(id2).setDiscoverFailure(true, 0);
            AtomicReference<BleFailure> err2 = new AtomicReference<>();
            List<BleConnectionState> s2 = new ArrayList<>();
            harness.client.connect(id2, BleConnectionConfig.builder().requestMtu(null).build(),
                    new BleConnectionObserver() {
                        @Override
                        public void onConnectionStateChanged(BleConnectionSnapshot snapshot) {
                            s2.add(snapshot.state());
                        }

                        @Override
                        public void onConnectionFailed(BleFailure failure) {
                            err2.set(failure);
                        }
                    });
            harness.drain();
            assertNotNull(err2.get());
            assertEquals(BleErrorCode.SERVICE_DISCOVERY_FAILED, err2.get().errorCode());
            assertEquals(BleConnectionState.FAILED, s2.get(s2.size() - 1));
        } finally {
            harness.release();
        }
    }

    @Test
    public void cancelDiscoverHandle_andDisconnectDuringBondedDelay_cleanupWaitersAndIgnoreLateCallback() {
        FakeYoBleHarness harness = new FakeYoBleHarness();
        BleDeviceId bondedId = new BleDeviceId("AA:BB:CC:DD:EE:1A");
        BleDeviceId activeId = new BleDeviceId("AA:BB:CC:DD:EE:1B");
        try {
            // 1. 断连发生在 bondedDiscoveryDelayMillis 延迟期间时应取消定时器，不再触发 discoverServices
            harness.bondPlatform.setBondState(bondedId, BleBondState.BONDED);
            FakeBleGattDevice bondedDev = harness.connectionPlatform.device(bondedId);
            harness.client.connect(bondedId, BleConnectionConfig.builder()
                    .bondedDiscoveryDelayMillis(600L)
                    .requestMtu(null)
                    .build(), new BleConnectionObserver() {});
            harness.drain();
            harness.advanceMillis(300L);
            assertEquals(0, bondedDev.discoverCalls());

            harness.client.disconnect(bondedId);
            harness.drain();
            harness.advanceMillis(1_000L);
            assertEquals(0, bondedDev.discoverCalls());

            // 2. discoverServices().cancel() 取消等待回调；陈旧 onServicesDiscovered(0) 安全丢弃
            FakeYoBleHarness refreshHarness = new FakeYoBleHarness(
                    BleConfig.builder().allowHiddenApiCacheRefresh(true).build());
            try {
                FakeBleGattDevice activeDev = refreshHarness.connectionPlatform.device(activeId);
                refreshHarness.client.connect(activeId, BleConnectionConfig.builder()
                        .requestMtu(null)
                        .build(), new BleConnectionObserver() {});
                refreshHarness.drain();

                activeDev.setDeferOperationCallbacks(true);
                AtomicReference<BleFailure> cancelledErr = new AtomicReference<>();
                BleOperationHandle discoverHandle = refreshHarness.client.discoverServices(
                        activeId, true, new BleValueObserver<BleGattDatabaseSnapshot>() {
                            @Override
                            public void onSuccess(BleGattDatabaseSnapshot value) {
                            }

                            @Override
                            public void onFailed(BleFailure failure) {
                                cancelledErr.set(failure);
                            }
                        });
                refreshHarness.drain();
                discoverHandle.cancel();
                refreshHarness.drain();

                assertNotNull(cancelledErr.get());
                assertEquals(BleErrorCode.OPERATION_CANCELLED, cancelledErr.get().errorCode());

                // 模拟底层在操作已取消后迟到的 onServicesDiscovered(0) -> 不抛异常、不覆盖崩溃
                activeDev.simulateServicesDiscovered(0);
                refreshHarness.drain();
                assertTrue(refreshHarness.client.getServices(activeId).isSuccess());
            } finally {
                refreshHarness.release();
            }
        } finally {
            harness.release();
        }
    }
}
