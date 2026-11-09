package com.yovo.lib_bluetooth.internal.ble.connection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_bluetooth.internal.ble.error.YoBleHciStatus;
import com.yovo.lib_bluetooth.internal.ble.testing.FakeBleGattDevice;
import com.yovo.lib_bluetooth.internal.ble.testing.FakeYoBleHarness;
import com.yovo.lib_bluetooth.model.ble.BleConnectionConfig;
import com.yovo.lib_bluetooth.model.ble.BleConnectionObserver;
import com.yovo.lib_bluetooth.model.ble.BleConnectionSnapshot;
import com.yovo.lib_bluetooth.model.ble.BleConnectionState;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleErrorCode;
import com.yovo.lib_bluetooth.model.ble.BleFailure;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * BLE-012 连接重试指数退避、200ms Close 间隔防护、null connectGatt 重试、
 * AutoConnect 意外断线重连（vs 主动断开绝不重连）及 BT OFF 瞬时兜底清理测试。
 */
public class BleConnectionRetryAndPowerOffTest {

    @Test
    public void retriesAfter133TwoTimesWith200msCloseGap_thenSucceedsOn3rdAttempt() {
        FakeYoBleHarness harness = new FakeYoBleHarness();
        BleDeviceId id = new BleDeviceId("AA:BB:CC:DD:EE:03");
        try {
            FakeBleGattDevice dev = harness.connectionPlatform.device(id);
            dev.enqueueConnectStatus(133)
                    .enqueueConnectStatus(YoBleHciStatus.CONNECTION_FAILED_TO_ESTABLISH)
                    .enqueueConnectStatus(0);

            List<BleConnectionState> states = new ArrayList<>();
            harness.client.connect(id, BleConnectionConfig.builder()
                    .maxRetryCount(2)
                    .retryInitialDelayMillis(100L)
                    .retryMaxDelayMillis(1_000L)
                    .closeDelayMillis(200L)
                    .requestMtu(null)
                    .build(), new BleConnectionObserver() {
                @Override
                public void onConnectionStateChanged(BleConnectionSnapshot snapshot) {
                    states.add(snapshot.state());
                }
            });
            harness.drain();

            // 第 1 次 133 立刻 close，由于 closeDelay=200ms (> initialDelay 100ms)，须等满 200ms
            assertEquals(1, dev.connectAttempts());
            assertTrue(dev.closeCalls() >= 1);

            harness.advanceMillis(199L);
            assertEquals("Must respect 200ms close gap before retry #1", 1, dev.connectAttempts());

            harness.advanceMillis(1L);
            // 第 2 次收到 0x3E 失败，retryAttempt=2 backoff=max(200,200)=200ms
            assertEquals(2, dev.connectAttempts());

            harness.advanceMillis(199L);
            assertEquals(2, dev.connectAttempts());

            harness.advanceMillis(1L);
            // 第 3 次成功进入 READY
            assertEquals(3, dev.connectAttempts());
            assertTrue(states.contains(BleConnectionState.READY));
        } finally {
            harness.release();
        }
    }

    @Test
    public void connectGattReturningNull_isRetriedAutomatically() {
        FakeYoBleHarness harness = new FakeYoBleHarness();
        BleDeviceId id = new BleDeviceId("AA:BB:CC:DD:EE:04");
        try {
            harness.connectionPlatform.setReturnNullConnects(1);
            List<BleConnectionState> states = new ArrayList<>();
            harness.client.connect(id, BleConnectionConfig.builder()
                    .maxRetryCount(1)
                    .retryInitialDelayMillis(250L)
                    .closeDelayMillis(200L)
                    .requestMtu(null)
                    .build(), new BleConnectionObserver() {
                @Override
                public void onConnectionStateChanged(BleConnectionSnapshot snapshot) {
                    states.add(snapshot.state());
                }
            });
            harness.drain();
            assertEquals(1, harness.connectionPlatform.totalConnectGattCalls());
            assertFalse(states.contains(BleConnectionState.READY));

            harness.advanceMillis(250L);
            assertEquals(2, harness.connectionPlatform.totalConnectGattCalls());
            assertTrue(states.contains(BleConnectionState.READY));
        } finally {
            harness.release();
        }
    }

    @Test
    public void autoConnect_reconnectsUnexpectedDrop_butNeverAfterUserDisconnect() {
        FakeYoBleHarness harness = new FakeYoBleHarness();
        BleDeviceId id = new BleDeviceId("AA:BB:CC:DD:EE:05");
        try {
            FakeBleGattDevice dev = harness.connectionPlatform.device(id);
            List<BleConnectionState> states = new ArrayList<>();
            harness.client.connect(id, BleConnectionConfig.builder()
                    .autoConnect(true)
                    .maxRetryCount(2)
                    .retryInitialDelayMillis(200L)
                    .closeDelayMillis(200L)
                    .requestMtu(null)
                    .build(), new BleConnectionObserver() {
                @Override
                public void onConnectionStateChanged(BleConnectionSnapshot snapshot) {
                    states.add(snapshot.state());
                }
            });
            harness.drain();
            assertEquals(1, dev.connectAttempts());
            assertEquals(BleConnectionState.READY, states.get(states.size() - 1));

            // 意外链路丢失 0x08 -> autoConnect=true 自动排期重连
            dev.simulateRemoteDisconnect(YoBleHciStatus.CONNECTION_TIMEOUT);
            harness.drain();
            harness.advanceMillis(250L);
            assertEquals(2, dev.connectAttempts());
            assertEquals(BleConnectionState.READY, states.get(states.size() - 1));

            // 主动 disconnect -> 绝不自动重连
            harness.client.disconnect(id);
            harness.drain();
            harness.advanceMillis(5_000L);
            assertEquals(2, dev.connectAttempts());
        } finally {
            harness.release();
        }
    }

    @Test
    public void bluetoothOff_abortsRetryLoopAndCleansConnectedSessionWithoutGattCallback() {
        FakeYoBleHarness harness = new FakeYoBleHarness();
        BleDeviceId retryId = new BleDeviceId("AA:BB:CC:DD:EE:06");
        BleDeviceId readyId = new BleDeviceId("AA:BB:CC:DD:EE:07");
        try {
            FakeBleGattDevice retryDev = harness.connectionPlatform.device(retryId);
            retryDev.enqueueConnectStatus(133);
            FakeBleGattDevice readyDev = harness.connectionPlatform.device(readyId);

            AtomicReference<BleFailure> retryErr = new AtomicReference<>();
            AtomicReference<BleFailure> readyErr = new AtomicReference<>();
            List<BleConnectionState> readyStates = new ArrayList<>();

            harness.client.connect(retryId, BleConnectionConfig.builder()
                    .maxRetryCount(3)
                    .retryInitialDelayMillis(500L)
                    .build(), new BleConnectionObserver() {
                @Override
                public void onConnectionFailed(BleFailure failure) {
                    retryErr.set(failure);
                }
            });
            harness.client.connect(readyId, BleConnectionConfig.builder()
                    .requestMtu(null)
                    .build(), new BleConnectionObserver() {
                @Override
                public void onConnectionStateChanged(BleConnectionSnapshot snapshot) {
                    readyStates.add(snapshot.state());
                }

                @Override
                public void onConnectionFailed(BleFailure failure) {
                    readyErr.set(failure);
                }
            });
            harness.drain();
            assertEquals(1, retryDev.connectAttempts());
            assertEquals(BleConnectionState.READY, readyStates.get(readyStates.size() - 1));

            // 模拟系统蓝牙关闭，且底层不发 onConnectionStateChange（Pixel 兜底场景）
            harness.env.setAdapterEnabled(false);
            harness.core.onAdapterTurnedOff();
            harness.drain();

            assertTrue(readyDev.isClosed());
            assertNotNull(readyErr.get());
            assertEquals(BleErrorCode.BLUETOOTH_DISABLED, readyErr.get().errorCode());
            assertEquals(BleConnectionState.DISCONNECTED, readyStates.get(readyStates.size() - 1));

            assertNotNull(retryErr.get());
            assertEquals(BleErrorCode.BLUETOOTH_DISABLED, retryErr.get().errorCode());

            // 推进时间确认重试定时器已被取消，不再产生第 2 次连接尝试
            harness.advanceMillis(10_000L);
            assertEquals(1, retryDev.connectAttempts());
        } finally {
            harness.release();
        }
    }

    @Test
    public void unexpectedLocalTerminated_0x16And0x100_skipsAutoReconnectAndFailsCleanly() {
        int[] localCodes = new int[]{
                YoBleHciStatus.CONNECTION_TERMINATED_BY_LOCAL_HOST,
                YoBleHciStatus.GATT_CONN_CANCEL
        };
        for (int idx = 0; idx < localCodes.length; idx++) {
            int code = localCodes[idx];
            FakeYoBleHarness harness = new FakeYoBleHarness();
            BleDeviceId id = new BleDeviceId("AA:BB:CC:DD:EE:0" + (8 + idx));
            try {
                FakeBleGattDevice dev = harness.connectionPlatform.device(id);
                List<BleConnectionState> states = new ArrayList<>();
                AtomicReference<BleFailure> failureRef = new AtomicReference<>();
                harness.client.connect(id, BleConnectionConfig.builder()
                        .autoConnect(true)
                        .maxRetryCount(3)
                        .retryInitialDelayMillis(200L)
                        .requestMtu(null)
                        .build(), new BleConnectionObserver() {
                    @Override
                    public void onConnectionStateChanged(BleConnectionSnapshot snapshot) {
                        states.add(snapshot.state());
                    }

                    @Override
                    public void onConnectionFailed(BleFailure failure) {
                        failureRef.set(failure);
                    }
                });
                harness.drain();
                assertEquals(BleConnectionState.READY, states.get(states.size() - 1));

                dev.simulateRemoteDisconnect(code);
                harness.drain();
                harness.advanceMillis(10_000L);

                assertEquals("HCI 0x16/0x100 must never auto-reconnect", 1, dev.connectAttempts());
                assertNotNull(failureRef.get());
                assertEquals(BleErrorCode.LOCAL_TERMINATED, failureRef.get().errorCode());
                assertEquals(code, failureRef.get().rawStatusCode());
                assertEquals(BleConnectionState.DISCONNECTED, states.get(states.size() - 1));
            } finally {
                harness.release();
            }
        }
    }

    @Test
    public void reconnectAfterUserDisconnect_succeedsUsingSavedConnectionConfig() {
        FakeYoBleHarness harness = new FakeYoBleHarness();
        BleDeviceId id = new BleDeviceId("AA:BB:CC:DD:EE:0A");
        try {
            FakeBleGattDevice dev = harness.connectionPlatform.device(id).setMaxSupportedMtu(247);
            harness.client.connect(id, BleConnectionConfig.builder()
                    .requestMtu(128)
                    .closeDelayMillis(200L)
                    .build(), new BleConnectionObserver() {});
            harness.drain();
            assertEquals(1, dev.connectAttempts());
            assertEquals(128, harness.client.getConnectionSnapshot(id).requireValue().mtu());

            assertTrue(harness.client.disconnect(id).isSuccess());
            harness.drain();

            // sessions 中已移除该设备，reconnect(id) 应复用保存的 BleConnectionConfig 重连成功
            assertTrue(harness.client.getConnectionSnapshot(id).isFailure());
            assertTrue(harness.client.reconnect(id).isSuccess());
            harness.drain();
            harness.advanceMillis(200L);

            assertEquals(2, dev.connectAttempts());
            BleConnectionSnapshot snap = harness.client.getConnectionSnapshot(id).requireValue();
            assertEquals(BleConnectionState.READY, snap.state());
            assertEquals(128, snap.mtu());
        } finally {
            harness.release();
        }
    }

    @Test
    public void androidConnectionPlatform_catchesConnectLeThrowableContractPresent() throws IOException {
        File moduleRoot = new File("src/main/java").isDirectory() ? new File(".") : new File("lib_bluetooth");
        String src = Files.readString(new File(moduleRoot,
                "src/main/java/com/yovo/lib_bluetooth/internal/ble/internal/android/gatt/YoBleAndroidConnectionPlatform.java")
                .toPath(), StandardCharsets.UTF_8);
        assertTrue(src.contains("gatt = YoBleGattCompat.connectLe(appContext, device, autoConnect, cb)"));
        assertTrue(src.contains("catch (Throwable t)"));
    }
}
