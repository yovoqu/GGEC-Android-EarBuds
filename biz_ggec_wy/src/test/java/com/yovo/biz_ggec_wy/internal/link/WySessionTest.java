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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class WySessionTest {

    private static class FakeRadioPort implements WyClassicRadioPort {
        SppStateListener sppListener;
        boolean controlConnected;
        boolean audioConnected;
        boolean disconnected;

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
}
