package com.yovo.lib_bluetooth.internal.ble.gatt;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_bluetooth.internal.ble.testing.FakeBleGattDevice;
import com.yovo.lib_bluetooth.internal.ble.testing.FakeYoBleHarness;
import com.yovo.lib_bluetooth.model.ble.BleConnectionConfig;
import com.yovo.lib_bluetooth.model.ble.BleConnectionObserver;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleGattCharacteristicInfo;
import com.yovo.lib_bluetooth.model.ble.BleGattDatabaseSnapshot;
import com.yovo.lib_bluetooth.model.ble.BleGattDescriptorInfo;
import com.yovo.lib_bluetooth.model.ble.BleGattServiceInfo;
import com.yovo.lib_bluetooth.model.ble.BleGattTarget;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * BLE-016 Service Changed (0x1801/0x2A05 on API < 31 与 API 31+ onServiceChanged) 自动重发现及合并测试。
 */
public class BleServiceChangedTest {

    private static final UUID SVC_A = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb");
    private static final UUID SVC_B = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb");
    private static final UUID CHR_DUMMY = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");

    @Test
    public void apiBelow31_subscribes0x2A05Indication_andRediscoversOnNotification() {
        FakeYoBleHarness harness = new FakeYoBleHarness();
        harness.env.setSdkInt(30);
        BleDeviceId id = new BleDeviceId("AA:BB:CC:DD:EE:20");
        try {
            FakeBleGattDevice dev = harness.connectionPlatform.device(id);
            BleGattServiceInfo gattSvc = genericAttributeServiceWith2A05();
            BleGattServiceInfo svcA = simpleService(SVC_A, 10);
            BleGattServiceInfo svcB = simpleService(SVC_B, 20);
            dev.setServices(Arrays.asList(gattSvc, svcA));

            List<BleGattDatabaseSnapshot> discovered = new ArrayList<>();
            harness.client.connect(id, BleConnectionConfig.builder()
                    .autoHandleServiceChanged(true)
                    .requestMtu(null)
                    .build(), new BleConnectionObserver() {
                @Override
                public void onServicesDiscovered(BleDeviceId d, BleGattDatabaseSnapshot database) {
                    discovered.add(database);
                }
            });
            harness.drain();

            assertEquals(1, dev.discoverCalls());
            assertEquals(1, dev.descWrites().size());
            assertArrayEquals(
                    YoBleNotificationRouter.ENABLE_INDICATION_VALUE,
                    dev.descWrites().get(0).value);
            assertEquals(1, discovered.size());

            // 远端新增 SVC_B 后触发 0x2A05 Indication
            dev.setServices(Arrays.asList(gattSvc, svcA, svcB));
            dev.simulateNotification(BleGattTarget.characteristic(
                    BleGattDatabaseSnapshot.GENERIC_ATTRIBUTE_SERVICE_UUID,
                    BleGattDatabaseSnapshot.SERVICE_CHANGED_CHARACTERISTIC_UUID),
                    new byte[]{0x01, 0x00, (byte) 0xFF, 0x00});
            harness.drain();

            assertEquals(2, dev.discoverCalls());
            assertEquals(2, discovered.size());
            assertNotNull(discovered.get(1).findService(SVC_B));
            assertNotNull(harness.client.getServices(id).getValue().findService(SVC_B));
        } finally {
            harness.release();
        }
    }

    @Test
    public void api31Plus_skipsCccdWrite_andReactsToSystemServiceChanged() {
        FakeYoBleHarness harness = new FakeYoBleHarness();
        harness.env.setSdkInt(31);
        BleDeviceId id = new BleDeviceId("AA:BB:CC:DD:EE:21");
        try {
            FakeBleGattDevice dev = harness.connectionPlatform.device(id);
            BleGattServiceInfo gattSvc = genericAttributeServiceWith2A05();
            BleGattServiceInfo svcA = simpleService(SVC_A, 10);
            BleGattServiceInfo svcB = simpleService(SVC_B, 20);
            dev.setServices(Arrays.asList(gattSvc, svcA));

            List<BleGattDatabaseSnapshot> discovered = new ArrayList<>();
            harness.client.connect(id, BleConnectionConfig.builder()
                    .autoHandleServiceChanged(true)
                    .requestMtu(null)
                    .build(), new BleConnectionObserver() {
                @Override
                public void onServicesDiscovered(BleDeviceId d, BleGattDatabaseSnapshot database) {
                    discovered.add(database);
                }
            });
            harness.drain();

            assertEquals(1, dev.discoverCalls());
            assertTrue("API 31+ should not manually write CCCD for 0x2A05", dev.descWrites().isEmpty());

            dev.setServices(Arrays.asList(gattSvc, svcA, svcB));
            dev.simulateServiceChangedApi31();
            harness.drain();

            assertEquals(2, dev.discoverCalls());
            assertEquals(2, discovered.size());
            assertNotNull(discovered.get(1).findService(SVC_B));
        } finally {
            harness.release();
        }
    }

    @Test
    public void noGenericAttributeService_doesNotWriteRedundantCccd() {
        FakeYoBleHarness harness = new FakeYoBleHarness();
        harness.env.setSdkInt(29);
        BleDeviceId id = new BleDeviceId("AA:BB:CC:DD:EE:22");
        try {
            FakeBleGattDevice dev = harness.connectionPlatform.device(id);
            dev.setServices(Collections.singletonList(simpleService(SVC_A, 10)));

            harness.client.connect(id, BleConnectionConfig.builder()
                    .autoHandleServiceChanged(true)
                    .requestMtu(null)
                    .build(), new BleConnectionObserver() {});
            harness.drain();

            assertEquals(1, dev.discoverCalls());
            assertTrue(dev.descWrites().isEmpty());
        } finally {
            harness.release();
        }
    }

    @Test
    public void multipleServiceChangedTriggersDuringDiscovery_coalesceIntoSingleRediscovery() {
        FakeYoBleHarness harness = new FakeYoBleHarness();
        harness.env.setSdkInt(31);
        BleDeviceId id = new BleDeviceId("AA:BB:CC:DD:EE:23");
        try {
            FakeBleGattDevice dev = harness.connectionPlatform.device(id);
            dev.setServices(Collections.singletonList(simpleService(SVC_A, 10)));
            dev.setDeferOperationCallbacks(true);

            harness.client.connect(id, BleConnectionConfig.builder()
                    .autoHandleServiceChanged(true)
                    .requestMtu(null)
                    .build(), new BleConnectionObserver() {});
            harness.drain();
            assertEquals(1, dev.discoverCalls());

            // 在初次发现尚在 in-flight 期间连续收到 3 次 ServiceChanged -> 应合并为仅 1 次后续重发现
            dev.setServices(Arrays.asList(simpleService(SVC_A, 10), simpleService(SVC_B, 20)));
            dev.simulateServiceChangedApi31();
            dev.simulateServiceChangedApi31();
            dev.simulateServiceChangedApi31();
            harness.drain();
            assertEquals(1, dev.discoverCalls());

            // 放行初次发现与合并后的二次发现
            dev.setDeferOperationCallbacks(false);
            dev.simulateServicesDiscovered(0);
            harness.drain();

            assertEquals(2, dev.discoverCalls());
            assertNotNull(harness.client.getServices(id).getValue().findService(SVC_B));
        } finally {
            harness.release();
        }
    }

    @Test
    public void repeated0x2A05OnApi30_clearsStaleDbBeforeRediscovery_andKeepsSingleCccdSubscription() {
        FakeYoBleHarness harness = new FakeYoBleHarness();
        harness.env.setSdkInt(30);
        BleDeviceId id = new BleDeviceId("AA:BB:CC:DD:EE:2F");
        try {
            FakeBleGattDevice dev = harness.connectionPlatform.device(id);
            BleGattServiceInfo gattSvc = genericAttributeServiceWith2A05();
            BleGattServiceInfo svcA = simpleService(SVC_A, 10);
            BleGattServiceInfo svcB = simpleService(SVC_B, 20);
            dev.setServices(Arrays.asList(gattSvc, svcA));

            harness.client.connect(id, BleConnectionConfig.builder()
                    .autoHandleServiceChanged(true)
                    .requestMtu(null)
                    .build(), new BleConnectionObserver() {});
            harness.drain();
            assertEquals(1, dev.descWrites().size());
            assertTrue(harness.client.getServices(id).isSuccess());

            // 触发首次 0x2A05 Service Changed 时，必须先清除陈旧库快照后再重发现
            dev.setDeferOperationCallbacks(true);
            dev.setServices(Arrays.asList(gattSvc, svcA, svcB));
            BleGattTarget scTarget = BleGattTarget.characteristic(
                    BleGattDatabaseSnapshot.GENERIC_ATTRIBUTE_SERVICE_UUID,
                    BleGattDatabaseSnapshot.SERVICE_CHANGED_CHARACTERISTIC_UUID);
            dev.simulateNotification(scTarget, new byte[]{0x01, 0x00, 0x50, 0x00});
            harness.drain();

            assertTrue("Stale database should be cleared during rediscovery",
                    harness.client.getServices(id).isFailure());

            dev.setDeferOperationCallbacks(false);
            dev.simulateServicesDiscovered(0);
            harness.drain();
            assertNotNull(harness.client.getServices(id).requireValue().findService(SVC_B));

            // 再次触发 0x2A05，不应重复叠加写入 CCCD
            dev.simulateNotification(scTarget, new byte[]{0x01, 0x00, 0x60, 0x00});
            harness.drain();
            assertEquals(3, dev.discoverCalls());
            assertEquals("Must reuse existing 0x2A05 CCCD subscription without duplicate writes",
                    1, dev.descWrites().size());
        } finally {
            harness.release();
        }
    }

    private static BleGattServiceInfo genericAttributeServiceWith2A05() {
        BleGattDescriptorInfo cccd = new BleGattDescriptorInfo(
                BleGattDatabaseSnapshot.CCCD_UUID,
                BleGattDescriptorInfo.PERMISSION_READ | BleGattDescriptorInfo.PERMISSION_WRITE);
        BleGattCharacteristicInfo sc = new BleGattCharacteristicInfo(
                BleGattDatabaseSnapshot.SERVICE_CHANGED_CHARACTERISTIC_UUID,
                2,
                BleGattCharacteristicInfo.PROPERTY_INDICATE,
                0,
                Collections.singletonList(cccd));
        return new BleGattServiceInfo(
                BleGattDatabaseSnapshot.GENERIC_ATTRIBUTE_SERVICE_UUID,
                1,
                true,
                Collections.singletonList(sc),
                Collections.emptyList());
    }

    private static BleGattServiceInfo simpleService(UUID svcUuid, int instanceId) {
        BleGattCharacteristicInfo ch = new BleGattCharacteristicInfo(
                CHR_DUMMY, instanceId + 1,
                BleGattCharacteristicInfo.PROPERTY_READ, 0, Collections.emptyList());
        return new BleGattServiceInfo(
                svcUuid, instanceId, true, Collections.singletonList(ch), Collections.emptyList());
    }
}
