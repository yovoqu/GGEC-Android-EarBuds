package com.yovo.lib_bluetooth.internal.ble.connection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * BLE-011 GATT 10 态有限状态机迁移约束与断连语义（USER / REMOTE 0x13 / LINK_LOSS 0x08 / CONNECTING 中断）测试。
 */
public class BleConnectionStateMachineTest {

    @Test
    public void stateMachine_enforces10StatesAndForbidsIllegalJumps() {
        YoBleConnectionStateMachine sm = new YoBleConnectionStateMachine();
        assertEquals(BleConnectionState.IDLE, sm.state());
        assertFalse(sm.canTransitionTo(BleConnectionState.READY));

        assertTrue(sm.transitionTo(BleConnectionState.CONNECTING, "connect"));
        assertTrue(sm.transitionTo(BleConnectionState.CONNECTED, "onConnected"));
        assertTrue(sm.isConnected());
        assertFalse(sm.isReady());

        assertTrue(sm.transitionTo(BleConnectionState.BONDING_WAIT, "bonding"));
        assertTrue(sm.isConnected());
        assertTrue(sm.transitionTo(BleConnectionState.DISCOVERING_SERVICES, "discover"));
        assertTrue(sm.transitionTo(BleConnectionState.INITIALIZING, "init"));
        assertTrue(sm.transitionTo(BleConnectionState.READY, "ready"));
        assertTrue(sm.isReady());

        assertTrue(sm.transitionTo(BleConnectionState.DISCONNECTING, "disconnect"));
        assertTrue(sm.transitionTo(BleConnectionState.DISCONNECTED, "closed"));
        assertFalse(sm.isConnected());

        // 禁止从 DISCONNECTED 直接跳跃到 READY / CONNECTED
        assertFalse(sm.canTransitionTo(BleConnectionState.READY));
        assertFalse("Illegal jump DISCONNECTED->READY must be rejected",
                sm.transitionTo(BleConnectionState.READY, "illegal"));
        assertEquals(BleConnectionState.DISCONNECTED, sm.state());
        assertFalse("Illegal jump DISCONNECTED->CONNECTED must be rejected",
                sm.transitionTo(BleConnectionState.CONNECTED, "illegal"));
        assertEquals(BleConnectionState.DISCONNECTED, sm.state());

        // 验证 FAILED 状态同样遵循状态机约束
        assertTrue(sm.transitionTo(BleConnectionState.CONNECTING, "retry"));
        assertTrue(sm.transitionTo(BleConnectionState.FAILED, "timeout"));
        assertFalse(sm.isConnected());
        assertFalse(sm.canTransitionTo(BleConnectionState.READY));
        assertFalse(sm.transitionTo(BleConnectionState.READY, "illegalFromFailed"));
        assertEquals(BleConnectionState.FAILED, sm.state());
    }

    @Test
    public void disconnectMidConnectingAndDiscovering_closesGattAndReleasesTimeouts() {
        FakeYoBleHarness harness = new FakeYoBleHarness();
        BleDeviceId id = new BleDeviceId("AA:BB:CC:DD:EE:01");
        try {
            FakeBleGattDevice dev = harness.connectionPlatform.device(id);
            // Case 1: CONNECTING 阶段（manualConnect）调用 disconnect -> close 并清空 connectTimeout
            dev.setManualConnectMode(true);
            List<BleConnectionState> states = new ArrayList<>();
            harness.client.connect(id, BleConnectionConfig.builder()
                    .connectTimeoutMillis(5_000L)
                    .maxRetryCount(0)
                    .build(), new BleConnectionObserver() {
                @Override
                public void onConnectionStateChanged(BleConnectionSnapshot snapshot) {
                    states.add(snapshot.state());
                }
            });
            harness.drain();
            assertEquals(BleConnectionState.CONNECTING, states.get(0));

            harness.client.disconnect(id);
            harness.drain();
            assertTrue(dev.isClosed());
            assertTrue(dev.disconnectCalls() >= 1);
            assertTrue(states.contains(BleConnectionState.DISCONNECTED));

            // 确认 connectTimeout 已释放，推进 6 秒无幽灵失败回调
            harness.advanceMillis(6_000L);
            assertEquals(1, dev.connectAttempts());

            // Case 2: DISCOVERING_SERVICES 中途调用 disconnect
            dev.setManualConnectMode(false).setDeferOperationCallbacks(true);
            states.clear();
            harness.advanceMillis(300L);
            harness.client.connect(id, BleConnectionConfig.builder().requestMtu(null).build(),
                    new BleConnectionObserver() {
                        @Override
                        public void onConnectionStateChanged(BleConnectionSnapshot snapshot) {
                            states.add(snapshot.state());
                        }
                    });
            harness.drain();
            assertTrue(states.contains(BleConnectionState.DISCOVERING_SERVICES));

            harness.client.disconnect(id);
            harness.drain();
            assertTrue(dev.isClosed());
            assertEquals(BleConnectionState.DISCONNECTED, states.get(states.size() - 1));
        } finally {
            harness.release();
        }
    }

    @Test
    public void remoteDisconnectReasons_map0x08And0x13Precisely() {
        assertRemoteDropReason(YoBleHciStatus.CONNECTION_TIMEOUT, BleErrorCode.LINK_LOSS);
        assertRemoteDropReason(YoBleHciStatus.REMOTE_USER_TERMINATED_CONNECTION, BleErrorCode.REMOTE_TERMINATED);
    }

    private static void assertRemoteDropReason(int rawHciStatus, BleErrorCode expected) {
        FakeYoBleHarness harness = new FakeYoBleHarness();
        BleDeviceId id = new BleDeviceId("AA:BB:CC:DD:EE:02");
        try {
            FakeBleGattDevice dev = harness.connectionPlatform.device(id);
            AtomicReference<BleFailure> failure = new AtomicReference<>();
            List<BleConnectionState> states = new ArrayList<>();

            harness.client.connect(id, BleConnectionConfig.builder()
                    .autoConnect(false)
                    .requestMtu(null)
                    .build(), new BleConnectionObserver() {
                @Override
                public void onConnectionStateChanged(BleConnectionSnapshot snapshot) {
                    states.add(snapshot.state());
                }

                @Override
                public void onConnectionFailed(BleFailure f) {
                    failure.set(f);
                }
            });
            harness.drain();
            assertTrue(states.contains(BleConnectionState.READY));

            dev.simulateRemoteDisconnect(rawHciStatus);
            harness.drain();
            assertNotNull(failure.get());
            assertEquals(expected, failure.get().errorCode());
            assertEquals(rawHciStatus, failure.get().rawStatusCode());
            assertEquals(BleConnectionState.DISCONNECTED, states.get(states.size() - 1));
        } finally {
            harness.release();
        }
    }
}
