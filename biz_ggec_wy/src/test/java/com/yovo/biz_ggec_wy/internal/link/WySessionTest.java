package com.yovo.biz_ggec_wy.internal.link;

import androidx.annotation.NonNull;

import com.yovo.biz_ggec_wy.api.WyControl;
import com.yovo.biz_ggec_wy.api.WyDevice;
import com.yovo.biz_ggec_wy.internal.center.catalog.WyCatalogImpl;
import com.yovo.biz_ggec_wy.internal.center.port.WyClassicRadioPort;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class WySessionTest {

    private static class FakeRadioPort implements WyClassicRadioPort {
        SppStateListener sppListener;
        boolean controlConnected;
        boolean audioConnected;
        boolean disconnected;
        final List<byte[]> sentControlFrames = new ArrayList<>();
        boolean failNextSend;

        @Override
        public void startDiscovery(@NonNull DiscoveryListener listener) {
        }

        @Override
        public void stopDiscovery() {
        }

        @Override
        public void connect(@NonNull String classicAddress, @NonNull SppStateListener listener) {
            this.sppListener = listener;
        }

        @Override
        public void disconnect() {
            this.disconnected = true;
            this.controlConnected = false;
            this.audioConnected = false;
        }

        @NonNull
        @Override
        public List<BondedDevice> getBondedDevices() {
            return List.of();
        }

        @Override
        public void connectAudioSpp(@NonNull ActionCallback callback) {
            audioConnected = true;
            callback.onResult(true, null);
        }

        @Override
        public void disconnectAudioSpp() {
            audioConnected = false;
        }

        @Override
        public void sendControl(@NonNull byte[] data, @NonNull SendCallback callback) {
            sentControlFrames.add(data);
            if (failNextSend) {
                failNextSend = false;
                callback.onComplete(false, WyControl.Failure.DEVICE_ERROR);
                return;
            }
            callback.onComplete(true, null);
        }

        @Override
        public boolean isControlConnected() {
            return controlConnected;
        }

        @Override
        public boolean isAudioConnected() {
            return audioConnected;
        }

        @Override
        public boolean isClassicConnected(@NonNull String classicAddress) {
            return true;
        }

        @Override
        public void release() {
        }
    }

    @Test
    public void testConnectionLifecycleAndAudioForwarding() {
        FakeRadioPort radio = new FakeRadioPort();
        WyCatalogImpl catalog = new WyCatalogImpl();
        WySession session = new WySession(radio, catalog, Runnable::run);

        List<WyControl.State> states = new ArrayList<>();
        session.observe(new WyControl.Listener() {
            @Override
            public void onConnectionChanged(WyControl.State state, WyControl.Failure failure) {
                states.add(state);
            }
        });

        assertEquals(WyControl.State.IDLE, session.connectionState());

        // 1. 发起连接
        session.connect("WY01", new WyDevice.Radios("11:22:33:44:55:66", "WY01-Test"));
        assertEquals(WyControl.State.CONNECTING, session.connectionState());
        assertEquals(1, states.size());
        assertEquals(WyControl.State.CONNECTING, states.get(0));

        // 2. 模拟控制流与音频流连上
        radio.controlConnected = true;
        radio.audioConnected = true;
        radio.sppListener.onControlStateChanged(WyClassicRadioPort.SppState.CONNECTED, null);
        radio.sppListener.onAudioStateChanged(WyClassicRadioPort.SppState.CONNECTED, null);

        assertEquals(WyControl.State.READY, session.connectionState());
        assertEquals(2, states.size());
        assertEquals(WyControl.State.READY, states.get(1));

        // 3. 验证音频流转发
        AtomicReference<byte[]> receivedAudio = new AtomicReference<>();
        session.setAudioChunkConsumer(receivedAudio::set);

        byte[] fakeChunk = new byte[]{1, 2, 3, 4};
        radio.sppListener.onAudioDataReceived(fakeChunk);
        assertNotNull(receivedAudio.get());
        assertEquals(4, receivedAudio.get().length);

        // 4. 断开连接
        session.disconnect();
        assertEquals(WyControl.State.DISCONNECTED, session.connectionState());
        assertTrue(radio.disconnected);
    }

    @Test
    public void testAudioSppDisconnectDoesNotDisconnectSession() {
        FakeRadioPort radio = new FakeRadioPort();
        WyCatalogImpl catalog = new WyCatalogImpl();
        WySession session = new WySession(radio, catalog, Runnable::run);

        List<WyControl.State> states = new ArrayList<>();
        session.observe(new WyControl.Listener() {
            @Override
            public void onConnectionChanged(WyControl.State state, WyControl.Failure failure) {
                states.add(state);
            }
        });

        session.connect("WY01", new WyDevice.Radios("11:22:33:44:55:66", "WY01-Test"));
        radio.controlConnected = true;
        radio.sppListener.onControlStateChanged(WyClassicRadioPort.SppState.CONNECTED, null);
        assertEquals(WyControl.State.READY, session.connectionState());

        // 模拟音频流连上后录音结束断开
        radio.audioConnected = true;
        radio.sppListener.onAudioStateChanged(WyClassicRadioPort.SppState.CONNECTED, null);
        radio.audioConnected = false;
        radio.sppListener.onAudioStateChanged(WyClassicRadioPort.SppState.DISCONNECTED, null);

        // 核心断言：音频流断开绝不影响全局就绪态
        assertEquals(WyControl.State.READY, session.connectionState());
        assertEquals(List.of(WyControl.State.CONNECTING, WyControl.State.READY), states);

        // 控制流断开时才触发整机断开
        radio.controlConnected = false;
        radio.sppListener.onControlStateChanged(WyClassicRadioPort.SppState.DISCONNECTED, WyControl.Failure.DISCONNECTED);
        assertEquals(WyControl.State.DISCONNECTED, session.connectionState());
        assertEquals(List.of(WyControl.State.CONNECTING, WyControl.State.READY, WyControl.State.DISCONNECTED), states);
    }

    @Test
    public void testSyncDeviceStateQueriesThreePartBatteryAndAcceptsActiveReport() {
        FakeRadioPort radio = new FakeRadioPort();
        WyCatalogImpl catalog = new WyCatalogImpl();
        WySession session = new WySession(radio, catalog, Runnable::run);

        List<WyDevice.Battery> eventBatteries = new ArrayList<>();
        List<WyDevice.Snapshot> snapshots = new ArrayList<>();
        session.observe(new WyControl.Listener() {
            @Override
            public void onSnapshotChanged(WyDevice.Snapshot snapshot) {
                snapshots.add(snapshot);
            }

            @Override
            public void onDeviceEvent(WyControl.Event event) {
                if (event instanceof WyControl.Event.BatteryChanged changed) {
                    eventBatteries.add(changed.battery());
                }
            }
        });

        session.connect("WY01", new WyDevice.Radios("11:22:33:44:55:66", "WY01-Test"));
        radio.controlConnected = true;
        radio.sppListener.onControlStateChanged(WyClassicRadioPort.SppState.CONNECTED, null);

        // 连接就绪后会自动发送一次三部分电量查询 99 EC 80 00 01 00 12 34
        assertEquals(1, radio.sentControlFrames.size());
        assertArrayEquals(WyBatteryProtocol.QUERY_ALL, radio.sentControlFrames.get(0));

        // 1. 测试 SyncDeviceState 动作
        final boolean[] started = {false};
        AtomicReference<WyControl.Outcome<Void>> syncResult = new AtomicReference<>();
        session.perform(new WyControl.Action.SyncDeviceState(), new WyControl.Callback() {
            @Override
            public void onStarted() {
                started[0] = true;
            }

            @Override
            public void onResult(WyControl.Outcome<Void> result) {
                syncResult.set(result);
            }
        });
        assertTrue(started[0]);
        assertEquals(2, radio.sentControlFrames.size());
        assertArrayEquals(WyBatteryProtocol.QUERY_ALL, radio.sentControlFrames.get(1));

        // 模拟设备回包：99 EC 80 00 04 00 50 5A 64 12 34 (80%, 90%, 100%)
        radio.sppListener.onControlDataReceived(new byte[]{
                (byte) 0x99, (byte) 0xEC, (byte) 0x80, 0x00, 0x04, 0x00,
                0x50, 0x5A, 0x64, 0x12, 0x34
        });
        assertTrue(syncResult.get() instanceof WyControl.Outcome.Ok);
        assertEquals(new WyDevice.Battery(80, 90, 100, false), session.snapshot().battery());
        assertEquals(1, eventBatteries.size());
        assertEquals(1, snapshots.size());

        // 2. 测试耳机主动上报三部分电量：99 EC 80 00 04 00 46 4B 5F 12 34 (70%, 75%, 95%)
        radio.sppListener.onControlDataReceived(new byte[]{
                (byte) 0x99, (byte) 0xEC, (byte) 0x80, 0x00, 0x04, 0x00,
                0x46, 0x4B, 0x5F, 0x12, 0x34
        });
        assertEquals(new WyDevice.Battery(70, 75, 95, false), session.snapshot().battery());
        assertEquals(2, eventBatteries.size());

        // 3. 测试 80 三个单路查询协议 (01/02/03) 及响应
        AtomicReference<WyControl.Outcome<Void>> leftResult = new AtomicReference<>();
        session.queryBattery(WyBatteryProtocol.PART_LEFT, leftResult::set);
        assertArrayEquals(WyBatteryProtocol.QUERY_LEFT, radio.sentControlFrames.get(2));
        radio.sppListener.onControlDataReceived(new byte[]{
                (byte) 0x99, (byte) 0xEC, (byte) 0x80, 0x00, 0x02, 0x01, 0x3C, 0x12, 0x34
        });
        assertTrue(leftResult.get() instanceof WyControl.Outcome.Ok);
        assertEquals(new WyDevice.Battery(60, 75, 95, false), session.snapshot().battery());

        session.queryBattery(WyBatteryProtocol.PART_RIGHT, result -> {});
        assertArrayEquals(WyBatteryProtocol.QUERY_RIGHT, radio.sentControlFrames.get(3));
        radio.sppListener.onControlDataReceived(new byte[]{
                (byte) 0x99, (byte) 0xEC, (byte) 0x80, 0x00, 0x02, 0x02, 0x41, 0x12, 0x34
        });
        assertEquals(new WyDevice.Battery(60, 65, 95, false), session.snapshot().battery());

        session.queryBattery(WyBatteryProtocol.PART_CASE, result -> {});
        assertArrayEquals(WyBatteryProtocol.QUERY_CASE, radio.sentControlFrames.get(4));
        radio.sppListener.onControlDataReceived(new byte[]{
                (byte) 0x99, (byte) 0xEC, (byte) 0x80, 0x00, 0x02, 0x03, 0x50, 0x12, 0x34
        });
        assertEquals(new WyDevice.Battery(60, 65, 80, false), session.snapshot().battery());
    }

    @Test
    public void testSyncDeviceStateFailsWhenDisconnected() {
        FakeRadioPort radio = new FakeRadioPort();
        WyCatalogImpl catalog = new WyCatalogImpl();
        WySession session = new WySession(radio, catalog, Runnable::run);

        AtomicReference<WyControl.Outcome<Void>> result = new AtomicReference<>();
        session.perform(new WyControl.Action.SyncDeviceState(), result::set);
        assertTrue(result.get() instanceof WyControl.Outcome.Err);
        assertEquals(WyControl.Failure.DISCONNECTED,
                ((WyControl.Outcome.Err<Void>) result.get()).failure());
    }
}
