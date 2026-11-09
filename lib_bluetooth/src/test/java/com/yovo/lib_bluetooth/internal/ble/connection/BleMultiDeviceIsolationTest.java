package com.yovo.lib_bluetooth.internal.ble.connection;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_bluetooth.internal.ble.error.YoBleHciStatus;
import com.yovo.lib_bluetooth.internal.ble.testing.FakeBleGattDevice;
import com.yovo.lib_bluetooth.internal.ble.testing.FakeYoBleHarness;
import com.yovo.lib_bluetooth.model.ble.BleConnectionConfig;
import com.yovo.lib_bluetooth.model.ble.BleConnectionObserver;
import com.yovo.lib_bluetooth.model.ble.BleConnectionSnapshot;
import com.yovo.lib_bluetooth.model.ble.BleConnectionState;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleGattCharacteristicInfo;
import com.yovo.lib_bluetooth.model.ble.BleGattDescriptorInfo;
import com.yovo.lib_bluetooth.model.ble.BleGattObserver;
import com.yovo.lib_bluetooth.model.ble.BleGattServiceInfo;
import com.yovo.lib_bluetooth.model.ble.BleGattTarget;
import com.yovo.lib_bluetooth.model.ble.BleNotificationObserver;
import com.yovo.lib_bluetooth.model.ble.BleNotificationSetupMode;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * BLE-013 多设备并发 GATT 会话严格隔离测试（4+ 设备、单台重试/断连完全隔离、重复连接复用/替换、disconnectAll）。
 */
public class BleMultiDeviceIsolationTest {

    private static final UUID SVC_UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb");
    private static final UUID CHR_UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb");
    private static final BleGattTarget TARGET = BleGattTarget.characteristic(SVC_UUID, CHR_UUID);

    @Test
    public void fourSimultaneousDevices_failureOnA_doesNotAffectBcdReadAndNotify() {
        FakeYoBleHarness harness = new FakeYoBleHarness();
        BleDeviceId devA = new BleDeviceId("AA:BB:CC:DD:EE:A1");
        BleDeviceId devB = new BleDeviceId("AA:BB:CC:DD:EE:B2");
        BleDeviceId devC = new BleDeviceId("AA:BB:CC:DD:EE:C3");
        BleDeviceId devD = new BleDeviceId("AA:BB:CC:DD:EE:D4");

        try {
            for (BleDeviceId id : new BleDeviceId[]{devA, devB, devC, devD}) {
                setupSimpleDevice(harness.connectionPlatform.device(id), id.value().getBytes());
            }
            // devA 首次连接遇到 133 进入重试中
            harness.connectionPlatform.device(devA).enqueueConnectStatus(133).enqueueConnectStatus(0);

            for (BleDeviceId id : new BleDeviceId[]{devA, devB, devC, devD}) {
                harness.client.connect(id, BleConnectionConfig.builder()
                        .maxRetryCount(2)
                        .retryInitialDelayMillis(500L)
                        .requestMtu(null)
                        .build(), new BleConnectionObserver() {});
            }
            harness.drain();

            // 此时 devA 在退避重试，B/C/D 均 READY
            List<BleConnectionSnapshot> connected = harness.client.getConnectedDevices().getValue();
            assertEquals(3, connected.size());

            // B/C 并发 Read，D 开启 Notify，全链路正常不阻塞
            List<byte[]> readB = new ArrayList<>();
            List<byte[]> readC = new ArrayList<>();
            List<byte[]> notifyD = new ArrayList<>();

            harness.client.read(devB, TARGET, new BleGattObserver() {
                @Override
                public void onRead(BleDeviceId d, BleGattTarget t, byte[] v) {
                    readB.add(v);
                }
            });
            harness.client.read(devC, TARGET, new BleGattObserver() {
                @Override
                public void onRead(BleDeviceId d, BleGattTarget t, byte[] v) {
                    readC.add(v);
                }
            });
            harness.client.subscribe(devD, TARGET, BleNotificationSetupMode.COMPAT,
                    new BleNotificationObserver() {
                        @Override
                        public void onCharacteristicChanged(BleDeviceId d, BleGattTarget t, byte[] v) {
                            notifyD.add(v);
                        }
                    });
            harness.drain();
            harness.connectionPlatform.device(devD).simulateNotification(TARGET, new byte[]{7, 8, 9});
            harness.drain();

            assertArrayEquals(devB.value().getBytes(), readB.get(0));
            assertArrayEquals(devC.value().getBytes(), readC.get(0));
            assertArrayEquals(new byte[]{7, 8, 9}, notifyD.get(0));

            // B 突然远端断连也不影响 C 与 D
            harness.connectionPlatform.device(devB).simulateRemoteDisconnect(YoBleHciStatus.CONNECTION_TIMEOUT);
            harness.drain();

            // 推进 500ms，devA 恢复 READY -> 当前连接设备应有 A/C/D = 3 台
            harness.advanceMillis(500L);
            assertEquals(3, harness.client.getConnectedDevices().getValue().size());

            // 重复 connect 已 READY 的 devC -> 复用同一会话而不重连底层 GATT
            int prevAttemptsC = harness.connectionPlatform.device(devC).connectAttempts();
            List<BleConnectionState> reuseStates = new ArrayList<>();
            harness.client.connect(devC, BleConnectionConfig.builder().build(), new BleConnectionObserver() {
                @Override
                public void onConnectionStateChanged(BleConnectionSnapshot s) {
                    reuseStates.add(s.state());
                }
            });
            harness.drain();
            assertEquals(prevAttemptsC, harness.connectionPlatform.device(devC).connectAttempts());
            assertTrue(reuseStates.contains(BleConnectionState.READY));

            // disconnectAll 原子清理全部连接
            harness.client.disconnectAll();
            harness.drain();
            assertEquals(0, harness.client.getConnectedDevices().getValue().size());
        } finally {
            harness.release();
        }
    }

    private static void setupSimpleDevice(FakeBleGattDevice dev, byte[] initialValue) {
        BleGattCharacteristicInfo ch = new BleGattCharacteristicInfo(
                CHR_UUID, 1,
                BleGattCharacteristicInfo.PROPERTY_READ | BleGattCharacteristicInfo.PROPERTY_NOTIFY,
                0, Collections.emptyList());
        BleGattServiceInfo svc = new BleGattServiceInfo(
                SVC_UUID, 1, true, Collections.singletonList(ch), Collections.emptyList());
        dev.setServices(Collections.singletonList(svc)).putValue(TARGET, initialValue);
    }
}
