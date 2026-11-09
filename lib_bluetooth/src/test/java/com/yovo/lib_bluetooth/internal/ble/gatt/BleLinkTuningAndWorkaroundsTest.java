package com.yovo.lib_bluetooth.internal.ble.gatt;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_bluetooth.internal.ble.testing.FakeBleGattDevice;
import com.yovo.lib_bluetooth.internal.ble.testing.FakeYoBleHarness;
import com.yovo.lib_bluetooth.model.ble.BleConnectionConfig;
import com.yovo.lib_bluetooth.model.ble.BleConnectionObserver;
import com.yovo.lib_bluetooth.model.ble.BleConnectionPriority;
import com.yovo.lib_bluetooth.model.ble.BleConnectionSnapshot;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleErrorCode;
import com.yovo.lib_bluetooth.model.ble.BleFailure;
import com.yovo.lib_bluetooth.model.ble.BleGattCharacteristicInfo;
import com.yovo.lib_bluetooth.model.ble.BleGattObserver;
import com.yovo.lib_bluetooth.model.ble.BleGattServiceInfo;
import com.yovo.lib_bluetooth.model.ble.BleGattTarget;
import com.yovo.lib_bluetooth.model.ble.BlePhy;
import com.yovo.lib_bluetooth.model.ble.BlePhyConfig;
import com.yovo.lib_bluetooth.model.ble.BlePhyOptions;
import com.yovo.lib_bluetooth.model.ble.BleValueObserver;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * BLE-022 链路调优（ConnectionPriority / 1M-2M-Coded PHY / RSSI）及 Android 厂商缺失回调兜底验证。
 */
public class BleLinkTuningAndWorkaroundsTest {

    private static final UUID SVC_UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb");
    private static final UUID CHR_UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");

    @Test
    public void connectionPriority_missingOnConnectionUpdated_completesAt200msAndUnblocksQueue() {
        FakeYoBleHarness harness = new FakeYoBleHarness();
        BleDeviceId id = new BleDeviceId("AA:BB:CC:DD:EE:51");
        try {
            FakeBleGattDevice dev = connectDevice(harness, id);
            dev.setSuppressConnectionPriorityCallback(true);
            BleGattTarget target = BleGattTarget.characteristic(SVC_UUID, CHR_UUID);
            dev.putValue(target, new byte[]{0x77});

            AtomicBoolean priorityDone = new AtomicBoolean(false);
            AtomicReference<byte[]> subsequentRead = new AtomicReference<>();

            harness.client.requestConnectionPriority(id, BleConnectionPriority.HIGH,
                    new BleValueObserver<Void>() {
                        @Override
                        public void onSuccess(Void value) {
                            priorityDone.set(true);
                        }
                    });
            harness.client.read(id, target, new BleGattObserver() {
                @Override
                public void onRead(BleDeviceId d, BleGattTarget t, byte[] value) {
                    subsequentRead.set(value);
                }
            });
            harness.drain();

            // 无回调且未满 200ms 时挂起等待
            assertFalse(priorityDone.get());
            harness.advanceMillis(YoBleLinkTuner.CONNECTION_PRIORITY_FALLBACK_MS - 1L);
            assertFalse(priorityDone.get());

            // 到达 200ms 触发兜底完成并立即出队后续 Read 请求，绝不卡死队列
            harness.advanceMillis(1L);
            assertTrue(priorityDone.get());
            assertArrayEquals(new byte[]{0x77}, subsequentRead.get());
        } finally {
            harness.release();
        }
    }

    @Test
    public void phyControl_apiBelow26Unsupported_andApi26PlusSupports1M2MCoded() {
        FakeYoBleHarness harness = new FakeYoBleHarness();
        BleDeviceId id = new BleDeviceId("AA:BB:CC:DD:EE:52");
        try {
            connectDevice(harness, id);

            // 1. API 25 (<26) 优雅降级返回 UNSUPPORTED_OPERATION
            harness.env.setSdkInt(25);
            List<BleFailure> preOreoFailures = new ArrayList<>();
            BleValueObserver<BlePhyConfig> failCollector = new BleValueObserver<BlePhyConfig>() {
                @Override
                public void onSuccess(BlePhyConfig value) {
                }

                @Override
                public void onFailed(BleFailure failure) {
                    preOreoFailures.add(failure);
                }
            };
            harness.client.setPreferredPhy(id, BlePhy.LE_2M, BlePhy.LE_2M, BlePhyOptions.NO_PREFERRED, failCollector);
            harness.client.readPhy(id, failCollector);
            harness.drain();

            assertEquals(2, preOreoFailures.size());
            assertEquals(BleErrorCode.UNSUPPORTED_OPERATION, preOreoFailures.get(0).errorCode());
            assertEquals(BleErrorCode.UNSUPPORTED_OPERATION, preOreoFailures.get(1).errorCode());

            // 2. API 28 支持 LE_2M 与 LE_CODED (S8) 设置和回读
            harness.env.setSdkInt(28);
            AtomicReference<BlePhyConfig> updated2m = new AtomicReference<>();
            AtomicReference<BlePhyConfig> readCoded = new AtomicReference<>();
            harness.client.setPreferredPhy(id, BlePhy.LE_2M, BlePhy.LE_2M, BlePhyOptions.NO_PREFERRED,
                    new BleValueObserver<BlePhyConfig>() {
                        @Override
                        public void onSuccess(BlePhyConfig value) {
                            updated2m.set(value);
                        }
                    });
            harness.client.setPreferredPhy(id, BlePhy.LE_CODED, BlePhy.LE_CODED, BlePhyOptions.S8,
                    new BleValueObserver<BlePhyConfig>() {
                        @Override
                        public void onSuccess(BlePhyConfig value) {
                        }
                    });
            harness.client.readPhy(id, new BleValueObserver<BlePhyConfig>() {
                @Override
                public void onSuccess(BlePhyConfig value) {
                    readCoded.set(value);
                }
            });
            harness.drain();

            assertEquals(BlePhyConfig.le2m(), updated2m.get());
            assertEquals(BlePhyConfig.leCoded(BlePhyOptions.S8), readCoded.get());
            assertEquals(BlePhyConfig.leCoded(BlePhyOptions.S8),
                    harness.client.getConnectionSnapshot(id).requireValue().phy());
        } finally {
            harness.release();
        }
    }

    @Test
    public void android13PhyWorkaround_missingOnPhyUpdate_triggersReadPhyAt1000ms() {
        FakeYoBleHarness harness = new FakeYoBleHarness();
        BleDeviceId id = new BleDeviceId("AA:BB:CC:DD:EE:53");
        try {
            FakeBleGattDevice dev = connectDevice(harness, id);
            harness.env.setSdkInt(33);
            dev.setSuppressSetPhyCallback(true);

            AtomicReference<BlePhyConfig> resultPhy = new AtomicReference<>();
            harness.client.setPreferredPhy(id, BlePhy.LE_2M, BlePhy.LE_2M, BlePhyOptions.NO_PREFERRED,
                    new BleValueObserver<BlePhyConfig>() {
                        @Override
                        public void onSuccess(BlePhyConfig value) {
                            resultPhy.set(value);
                        }
                    });
            harness.drain();

            assertEquals(0, dev.readPhyCalls());
            assertEquals(null, resultPhy.get());

            harness.advanceMillis(YoBleLinkTuner.ANDROID_13_PHY_FALLBACK_MS - 1L);
            assertEquals(0, dev.readPhyCalls());
            assertEquals(null, resultPhy.get());

            // 1000ms 到期后主动调用 transport.readPhy() 恢复 SET_PHY 队列并同步更新连接快照
            harness.advanceMillis(1L);
            assertEquals(1, dev.readPhyCalls());
            assertEquals(BlePhyConfig.le2m(), resultPhy.get());
            assertEquals(BlePhyConfig.le2m(),
                    harness.client.getConnectionSnapshot(id).requireValue().phy());
        } finally {
            harness.release();
        }
    }

    @Test
    public void readRemoteRssi_returnsLiveValueAndUpdatesSnapshotAndObserver() {
        FakeYoBleHarness harness = new FakeYoBleHarness();
        BleDeviceId id = new BleDeviceId("AA:BB:CC:DD:EE:54");
        try {
            AtomicInteger observerRssi = new AtomicInteger();
            FakeBleGattDevice dev = harness.connectionPlatform.device(id);
            BleGattServiceInfo svc = new BleGattServiceInfo(
                    SVC_UUID, 1, true,
                    Collections.singletonList(new BleGattCharacteristicInfo(
                            CHR_UUID, 1, BleGattCharacteristicInfo.PROPERTY_READ, 0,
                            Collections.emptyList())),
                    Collections.emptyList());
            dev.setServices(Collections.singletonList(svc));
            dev.setRemoteRssi(-62);

            harness.client.connect(id, BleConnectionConfig.builder().requestMtu(null).build(),
                    new BleConnectionObserver() {
                        @Override
                        public void onRssiRead(BleDeviceId d, int rssi) {
                            observerRssi.set(rssi);
                        }
                    });
            harness.drain();

            AtomicInteger callbackRssi = new AtomicInteger();
            harness.client.readRemoteRssi(id, new BleValueObserver<Integer>() {
                @Override
                public void onSuccess(Integer value) {
                    callbackRssi.set(value);
                }
            });
            harness.drain();

            assertEquals(-62, callbackRssi.get());
            assertEquals(-62, observerRssi.get());
            BleConnectionSnapshot snap = harness.client.getConnectionSnapshot(id).requireValue();
            assertEquals(Integer.valueOf(-62), snap.lastRssi());
        } finally {
            harness.release();
        }
    }

    private static FakeBleGattDevice connectDevice(FakeYoBleHarness harness, BleDeviceId id) {
        FakeBleGattDevice dev = harness.connectionPlatform.device(id);
        BleGattCharacteristicInfo ch = new BleGattCharacteristicInfo(
                CHR_UUID, 1, BleGattCharacteristicInfo.PROPERTY_READ, 0, Collections.emptyList());
        BleGattServiceInfo svc = new BleGattServiceInfo(
                SVC_UUID, 1, true, Collections.singletonList(ch), Collections.emptyList());
        dev.setServices(Collections.singletonList(svc));
        harness.client.connect(id, BleConnectionConfig.builder().requestMtu(null).build(),
                new BleConnectionObserver() {});
        harness.drain();
        return dev;
    }
}
