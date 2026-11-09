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
import com.yovo.lib_bluetooth.model.ble.BleErrorCode;
import com.yovo.lib_bluetooth.model.ble.BleFailure;
import com.yovo.lib_bluetooth.model.ble.BleGattCharacteristicInfo;
import com.yovo.lib_bluetooth.model.ble.BleGattDatabaseSnapshot;
import com.yovo.lib_bluetooth.model.ble.BleGattDescriptorInfo;
import com.yovo.lib_bluetooth.model.ble.BleGattServiceInfo;
import com.yovo.lib_bluetooth.model.ble.BleGattTarget;
import com.yovo.lib_bluetooth.model.ble.BleNotificationObserver;
import com.yovo.lib_bluetooth.model.ble.BleNotificationSetupMode;
import com.yovo.lib_bluetooth.model.ble.BleOperationHandle;
import com.yovo.lib_bluetooth.model.ble.BleGattObserver;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * BLE-018 Notify vs Indicate 智能 CCCD 写入、引用计数多播路由、DEFAULT/COMPAT/QUICK_SETUP 与多实例隔离测试。
 */
public class BleNotificationRouterTest {

    private static final UUID SVC_UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb");
    private static final UUID CHR_NOTIFY = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb");
    private static final UUID CHR_INDICATE = UUID.fromString("00002a38-0000-1000-8000-00805f9b34fb");
    private static final UUID CHR_NO_CCCD = UUID.fromString("00002a39-0000-1000-8000-00805f9b34fb");
    private static final UUID CHR_DUP = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");

    @Test
    public void notifyVsIndicate_writesMatchingCccdValue() {
        FakeYoBleHarness harness = new FakeYoBleHarness();
        BleDeviceId id = new BleDeviceId("AA:BB:CC:DD:EE:26");
        try {
            FakeBleGattDevice dev = setupRichDevice(harness, id);

            BleGattTarget notifyTarget = BleGattTarget.characteristic(SVC_UUID, CHR_NOTIFY);
            BleGattTarget indicateTarget = BleGattTarget.characteristic(SVC_UUID, CHR_INDICATE);

            List<byte[]> receivedNotify = new ArrayList<>();
            List<byte[]> receivedIndicate = new ArrayList<>();

            harness.client.subscribe(id, notifyTarget, BleNotificationSetupMode.DEFAULT,
                    new BleNotificationObserver() {
                        @Override
                        public void onCharacteristicChanged(BleDeviceId d, BleGattTarget t, byte[] value) {
                            receivedNotify.add(value);
                        }
                    });
            harness.client.subscribe(id, indicateTarget, BleNotificationSetupMode.DEFAULT,
                    new BleNotificationObserver() {
                        @Override
                        public void onCharacteristicChanged(BleDeviceId d, BleGattTarget t, byte[] value) {
                            receivedIndicate.add(value);
                        }
                    });
            harness.drain();

            assertEquals(2, dev.descWrites().size());
            assertArrayEquals(YoBleNotificationRouter.ENABLE_NOTIFICATION_VALUE, dev.descWrites().get(0).value);
            assertArrayEquals(YoBleNotificationRouter.ENABLE_INDICATION_VALUE, dev.descWrites().get(1).value);

            dev.simulateNotification(notifyTarget, new byte[]{0x01, 0x02});
            dev.simulateNotification(indicateTarget, new byte[]{0x03, 0x04});
            harness.drain();

            assertEquals(1, receivedNotify.size());
            assertArrayEquals(new byte[]{0x01, 0x02}, receivedNotify.get(0));
            assertEquals(1, receivedIndicate.size());
            assertArrayEquals(new byte[]{0x03, 0x04}, receivedIndicate.get(0));
        } finally {
            harness.release();
        }
    }

    @Test
    public void refCountedCccd_twoSubscribersShareEnable_andDisableOnlyOnLastCancel() {
        FakeYoBleHarness harness = new FakeYoBleHarness();
        BleDeviceId id = new BleDeviceId("AA:BB:CC:DD:EE:27");
        try {
            FakeBleGattDevice dev = setupRichDevice(harness, id);
            BleGattTarget target = BleGattTarget.characteristic(SVC_UUID, CHR_NOTIFY);

            List<byte[]> subARx = new ArrayList<>();
            List<byte[]> subBRx = new ArrayList<>();
            AtomicBoolean subAEnabled = new AtomicBoolean(false);
            AtomicBoolean subBEnabled = new AtomicBoolean(false);
            AtomicBoolean subBDisabled = new AtomicBoolean(false);

            BleOperationHandle hA = harness.client.subscribe(id, target, BleNotificationSetupMode.DEFAULT,
                    new BleNotificationObserver() {
                        @Override
                        public void onNotificationEnabled(BleDeviceId d, BleGattTarget t) {
                            subAEnabled.set(true);
                        }

                        @Override
                        public void onCharacteristicChanged(BleDeviceId d, BleGattTarget t, byte[] value) {
                            subARx.add(value);
                        }
                    });
            BleOperationHandle hB = harness.client.subscribe(id, target, BleNotificationSetupMode.DEFAULT,
                    new BleNotificationObserver() {
                        @Override
                        public void onNotificationEnabled(BleDeviceId d, BleGattTarget t) {
                            subBEnabled.set(true);
                        }

                        @Override
                        public void onNotificationDisabled(BleDeviceId d, BleGattTarget t) {
                            subBDisabled.set(true);
                        }

                        @Override
                        public void onCharacteristicChanged(BleDeviceId d, BleGattTarget t, byte[] value) {
                            subBRx.add(value);
                        }
                    });
            harness.drain();

            assertTrue(subAEnabled.get());
            assertTrue(subBEnabled.get());
            // 0 -> 1 仅写一次 ENABLE CCCD
            assertEquals(1, dev.descWrites().size());

            dev.simulateNotification(target, new byte[]{0x10});
            harness.drain();
            assertEquals(1, subARx.size());
            assertEquals(1, subBRx.size());

            // 取消订阅者 A (2 -> 1)：不应关闭底层 CCCD，订阅者 B 仍持续收包
            hA.cancel();
            harness.drain();
            assertEquals(1, dev.descWrites().size());

            dev.simulateNotification(target, new byte[]{0x20});
            harness.drain();
            assertEquals(1, subARx.size());
            assertEquals(2, subBRx.size());

            // 取消最后一个订阅者 B (1 -> 0)：写 {0x00, 0x00} 关闭 CCCD
            hB.cancel();
            harness.drain();
            assertTrue(subBDisabled.get());
            assertEquals(2, dev.descWrites().size());
            assertArrayEquals(YoBleNotificationRouter.DISABLE_NOTIFICATION_VALUE, dev.descWrites().get(1).value);
        } finally {
            harness.release();
        }
    }

    @Test
    public void setupModes_defaultVsCompatWithoutCccd_andQuickSetupImmediateReady() {
        FakeYoBleHarness harness = new FakeYoBleHarness();
        BleDeviceId id = new BleDeviceId("AA:BB:CC:DD:EE:28");
        try {
            FakeBleGattDevice dev = setupRichDevice(harness, id);
            BleGattTarget noCccdTarget = BleGattTarget.characteristic(SVC_UUID, CHR_NO_CCCD);
            BleGattTarget withCccdTarget = BleGattTarget.characteristic(SVC_UUID, CHR_NOTIFY);

            // DEFAULT 模式遇到无 CCCD 报错 DESCRIPTOR_NOT_FOUND
            AtomicReference<BleFailure> defaultErr = new AtomicReference<>();
            harness.client.subscribe(id, noCccdTarget, BleNotificationSetupMode.DEFAULT,
                    new BleNotificationObserver() {
                        @Override
                        public void onCharacteristicChanged(BleDeviceId d, BleGattTarget t, byte[] v) {
                        }

                        @Override
                        public void onNotificationFailed(BleFailure failure) {
                            defaultErr.set(failure);
                        }
                    });
            harness.drain();
            assertNotNull(defaultErr.get());
            assertEquals(BleErrorCode.DESCRIPTOR_NOT_FOUND, defaultErr.get().errorCode());

            // COMPAT 模式遇到无 CCCD 仅调本地 setCharacteristicNotification 即可就绪
            AtomicBoolean compatEnabled = new AtomicBoolean(false);
            harness.client.subscribe(id, noCccdTarget, BleNotificationSetupMode.COMPAT,
                    new BleNotificationObserver() {
                        @Override
                        public void onNotificationEnabled(BleDeviceId d, BleGattTarget t) {
                            compatEnabled.set(true);
                        }

                        @Override
                        public void onCharacteristicChanged(BleDeviceId d, BleGattTarget t, byte[] v) {
                        }
                    });
            harness.drain();
            assertTrue(compatEnabled.get());
            assertTrue(dev.descWrites().isEmpty());

            // QUICK_SETUP 即使 CCCD 写回调挂起，也立刻通知 onNotificationEnabled
            dev.setDeferOperationCallbacks(true);
            AtomicBoolean quickEnabled = new AtomicBoolean(false);
            harness.client.subscribe(id, withCccdTarget, BleNotificationSetupMode.QUICK_SETUP,
                    new BleNotificationObserver() {
                        @Override
                        public void onNotificationEnabled(BleDeviceId d, BleGattTarget t) {
                            quickEnabled.set(true);
                        }

                        @Override
                        public void onCharacteristicChanged(BleDeviceId d, BleGattTarget t, byte[] v) {
                        }
                    });
            harness.drain();
            assertTrue(quickEnabled.get());
            assertEquals(1, dev.descWrites().size());
        } finally {
            harness.release();
        }
    }

    @Test
    public void multiInstanceCharacteristics_routeNotificationsWithoutCrosstalk() {
        FakeYoBleHarness harness = new FakeYoBleHarness();
        BleDeviceId id = new BleDeviceId("AA:BB:CC:DD:EE:29");
        try {
            FakeBleGattDevice dev = setupRichDevice(harness, id);
            BleGattTarget t101 = BleGattTarget.characteristic(SVC_UUID, 1, CHR_DUP, 101);
            BleGattTarget t102 = BleGattTarget.characteristic(SVC_UUID, 1, CHR_DUP, 102);

            List<byte[]> rx101 = new ArrayList<>();
            List<byte[]> rx102 = new ArrayList<>();

            harness.client.subscribe(id, t101, BleNotificationSetupMode.DEFAULT,
                    new BleNotificationObserver() {
                        @Override
                        public void onCharacteristicChanged(BleDeviceId d, BleGattTarget t, byte[] v) {
                            rx101.add(v);
                        }
                    });
            harness.client.subscribe(id, t102, BleNotificationSetupMode.DEFAULT,
                    new BleNotificationObserver() {
                        @Override
                        public void onCharacteristicChanged(BleDeviceId d, BleGattTarget t, byte[] v) {
                            rx102.add(v);
                        }
                    });
            harness.drain();

            dev.simulateNotification(t101, new byte[]{0x11});
            dev.simulateNotification(t102, new byte[]{0x22});
            harness.drain();

            assertEquals(1, rx101.size());
            assertArrayEquals(new byte[]{0x11}, rx101.get(0));
            assertEquals(1, rx102.size());
            assertArrayEquals(new byte[]{0x22}, rx102.get(0));
        } finally {
            harness.release();
        }
    }

    @Test
    public void setNotifyCompat_cancelPendingQueueOp_emitsCancelledAndSkipsCccdWrite() {
        FakeYoBleHarness harness = new FakeYoBleHarness();
        BleDeviceId id = new BleDeviceId("AA:BB:CC:DD:EE:2A");
        try {
            FakeBleGattDevice dev = setupRichDevice(harness, id);
            dev.setDeferOperationCallbacks(true);
            BleGattTarget notifyTarget = BleGattTarget.characteristic(SVC_UUID, CHR_NOTIFY);

            // 先入队一个挂起读操作占住 inFlight 槽位
            harness.client.read(id, notifyTarget, new BleGattObserver() {});
            AtomicReference<BleFailure> errRef = new AtomicReference<>();
            BleOperationHandle notifyHandle = harness.client.setNotify(
                    id, notifyTarget, true, new BleGattObserver() {
                        @Override
                        public void onFailed(BleFailure failure) {
                            errRef.set(failure);
                        }
                    });
            harness.drain();

            notifyHandle.cancel();
            harness.drain();

            assertNotNull(errRef.get());
            assertEquals(BleErrorCode.OPERATION_CANCELLED, errRef.get().errorCode());
            assertTrue("Cancelled queued setNotify must not write CCCD", dev.descWrites().isEmpty());
        } finally {
            harness.release();
        }
    }

    private static FakeBleGattDevice setupRichDevice(FakeYoBleHarness harness, BleDeviceId id) {
        FakeBleGattDevice dev = harness.connectionPlatform.device(id);
        BleGattDescriptorInfo cccd = new BleGattDescriptorInfo(
                BleGattDatabaseSnapshot.CCCD_UUID,
                BleGattDescriptorInfo.PERMISSION_READ | BleGattDescriptorInfo.PERMISSION_WRITE);

        BleGattCharacteristicInfo notifyChar = new BleGattCharacteristicInfo(
                CHR_NOTIFY, 11, BleGattCharacteristicInfo.PROPERTY_NOTIFY, 0,
                Collections.singletonList(cccd));
        BleGattCharacteristicInfo indicateChar = new BleGattCharacteristicInfo(
                CHR_INDICATE, 12, BleGattCharacteristicInfo.PROPERTY_INDICATE, 0,
                Collections.singletonList(cccd));
        BleGattCharacteristicInfo noCccdChar = new BleGattCharacteristicInfo(
                CHR_NO_CCCD, 13, BleGattCharacteristicInfo.PROPERTY_NOTIFY, 0,
                Collections.emptyList());
        BleGattCharacteristicInfo dup101 = new BleGattCharacteristicInfo(
                CHR_DUP, 101, BleGattCharacteristicInfo.PROPERTY_NOTIFY, 0,
                Collections.singletonList(cccd));
        BleGattCharacteristicInfo dup102 = new BleGattCharacteristicInfo(
                CHR_DUP, 102, BleGattCharacteristicInfo.PROPERTY_NOTIFY, 0,
                Collections.singletonList(cccd));

        BleGattServiceInfo svc = new BleGattServiceInfo(
                SVC_UUID, 1, true,
                Arrays.asList(notifyChar, indicateChar, noCccdChar, dup101, dup102),
                Collections.emptyList());
        dev.setServices(Collections.singletonList(svc));
        harness.client.connect(id, BleConnectionConfig.builder().requestMtu(null).build(),
                new BleConnectionObserver() {});
        harness.drain();
        return dev;
    }
}
