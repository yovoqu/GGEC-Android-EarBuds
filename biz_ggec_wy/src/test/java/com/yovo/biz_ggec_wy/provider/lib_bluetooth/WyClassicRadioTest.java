package com.yovo.biz_ggec_wy.provider.lib_bluetooth;

import androidx.annotation.NonNull;

import com.yovo.biz_ggec_wy.api.WyControl;
import com.yovo.biz_ggec_wy.internal.center.port.WyClassicRadioPort;
import com.yovo.lib_bluetooth.api.YoCbtClient;
import com.yovo.lib_bluetooth.model.classic.CbtAclLinkObserver;
import com.yovo.lib_bluetooth.model.classic.CbtAclLinkState;
import com.yovo.lib_bluetooth.model.classic.CbtBondState;
import com.yovo.lib_bluetooth.model.classic.CbtBondStateObserver;
import com.yovo.lib_bluetooth.model.classic.CbtCommand;
import com.yovo.lib_bluetooth.model.classic.CbtCommandObserver;
import com.yovo.lib_bluetooth.model.classic.CbtConnectedDeviceObserver;
import com.yovo.lib_bluetooth.model.classic.CbtDeviceId;
import com.yovo.lib_bluetooth.model.classic.CbtDeviceIdentity;
import com.yovo.lib_bluetooth.model.classic.CbtDeviceProfileSnapshot;
import com.yovo.lib_bluetooth.model.classic.CbtDeviceProfileState;
import com.yovo.lib_bluetooth.model.classic.CbtDeviceSnapshot;
import com.yovo.lib_bluetooth.model.classic.CbtDiagnosticReport;
import com.yovo.lib_bluetooth.model.classic.CbtDiscoveryConfig;
import com.yovo.lib_bluetooth.model.classic.CbtDiscoveryObserver;
import com.yovo.lib_bluetooth.model.classic.CbtOperationHandle;
import com.yovo.lib_bluetooth.model.classic.CbtProfileAggregateState;
import com.yovo.lib_bluetooth.model.classic.CbtProfileConnectionState;
import com.yovo.lib_bluetooth.model.classic.CbtProfileStateObserver;
import com.yovo.lib_bluetooth.model.classic.CbtProfileType;
import com.yovo.lib_bluetooth.model.classic.CbtServiceDiscoveryConfig;
import com.yovo.lib_bluetooth.model.classic.CbtServiceDiscoveryObserver;
import com.yovo.lib_bluetooth.model.classic.CbtSppConfig;
import com.yovo.lib_bluetooth.model.classic.CbtSppConnectionId;
import com.yovo.lib_bluetooth.model.classic.CbtSppConnectionObserver;
import com.yovo.lib_bluetooth.model.classic.CbtSppConnectionSnapshot;
import com.yovo.lib_bluetooth.model.classic.CbtSppConnectionState;
import com.yovo.lib_bluetooth.model.classic.CbtSppEndpoint;
import com.yovo.lib_bluetooth.model.classic.CbtSppStateObserver;
import com.yovo.lib_bluetooth.model.common.Result;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class WyClassicRadioTest {

    private static final String TARGET_MAC = "11:22:33:44:55:66";
    private static final CbtDeviceId TARGET_ID = CbtDeviceId.fromAddress(TARGET_MAC);

    private static CbtDeviceSnapshot createSnapshot(CbtBondState bond, CbtAclLinkState acl) {
        return new CbtDeviceSnapshot(
                new CbtDeviceIdentity(TARGET_ID, TARGET_MAC, "WY01"),
                bond,
                acl,
                -50, 0, 0, List.of(), new java.util.EnumMap<>(CbtProfileType.class), List.of(), List.of());
    }

    private static class FakeDelay implements WyClassicRadio.Delay {
        Runnable pendingTask;
        long delayMillis;

        @Override
        public void postDelayed(@NonNull Runnable task, long delayMillis) {
            this.pendingTask = task;
            this.delayMillis = delayMillis;
        }

        @Override
        public void removeCallbacks(@NonNull Runnable task) {
            if (this.pendingTask == task) {
                this.pendingTask = null;
            }
        }

        void fire() {
            if (pendingTask != null) {
                Runnable task = pendingTask;
                pendingTask = null;
                task.run();
            }
        }
    }

    private static class RecordingListener implements WyClassicRadioPort.SppStateListener {
        final List<WyClassicRadioPort.SppState> controlStates = new ArrayList<>();
        final List<WyControl.Failure> controlFailures = new ArrayList<>();
        final List<WyClassicRadioPort.SppState> audioStates = new ArrayList<>();

        @Override
        public void onControlStateChanged(@NonNull WyClassicRadioPort.SppState state, WyControl.Failure failure) {
            controlStates.add(state);
            controlFailures.add(failure);
        }

        @Override
        public void onAudioStateChanged(@NonNull WyClassicRadioPort.SppState state, WyControl.Failure failure) {
            audioStates.add(state);
        }

        @Override
        public void onControlDataReceived(@NonNull byte[] data) {
        }

        @Override
        public void onAudioDataReceived(@NonNull byte[] data) {
        }
    }

    private static class FakeYoCbtClient implements YoCbtClient {
        boolean aclConnected;
        boolean bonded;
        int createBondCalls;
        int connectAudioCalls;
        int sppConnectCalls;

        CbtAclLinkObserver aclObserver;
        CbtBondStateObserver bondObserver;
        CbtProfileStateObserver profileObserver;
        final List<CbtSppConnectionObserver> sppObservers = new ArrayList<>();

        @NonNull
        @Override
        public Result<CbtAclLinkState> getAclState(@NonNull CbtDeviceId deviceId) {
            return Result.success(aclConnected ? CbtAclLinkState.CONNECTED : CbtAclLinkState.DISCONNECTED);
        }

        @NonNull
        @Override
        public Result<List<CbtProfileType>> getConnectedProfiles(@NonNull CbtDeviceId deviceId) {
            return Result.success(List.of());
        }

        @NonNull
        @Override
        public Result<List<CbtDeviceSnapshot>> getBondedDevices() {
            if (bonded) {
                return Result.success(List.of(createSnapshot(CbtBondState.BONDED, CbtAclLinkState.DISCONNECTED)));
            }
            return Result.success(List.of());
        }

        @NonNull
        @Override
        public Result<Void> createBond(@NonNull CbtDeviceId deviceId) {
            createBondCalls++;
            return Result.success(null);
        }

        @NonNull
        @Override
        public Result<Void> connectAudio(@NonNull CbtDeviceId deviceId) {
            connectAudioCalls++;
            return Result.success(null);
        }

        @NonNull
        @Override
        public CbtOperationHandle observeAclLinkState(@NonNull CbtAclLinkObserver observer) {
            this.aclObserver = observer;
            return new CbtOperationHandle("acl-op", () -> this.aclObserver = null);
        }

        @NonNull
        @Override
        public CbtOperationHandle observeBondState(@NonNull CbtBondStateObserver observer) {
            this.bondObserver = observer;
            return new CbtOperationHandle("bond-op", () -> this.bondObserver = null);
        }

        @NonNull
        @Override
        public CbtOperationHandle observeProfileState(@NonNull CbtDeviceId deviceId,
                                                      @NonNull CbtProfileStateObserver observer) {
            this.profileObserver = observer;
            return new CbtOperationHandle("prof-op", () -> this.profileObserver = null);
        }

        @NonNull
        @Override
        public CbtOperationHandle connectSpp(@NonNull CbtDeviceId deviceId,
                                              @NonNull CbtSppEndpoint endpoint,
                                              @NonNull CbtSppConfig config,
                                              @NonNull CbtSppConnectionObserver observer) {
            sppConnectCalls++;
            sppObservers.add(observer);
            return new CbtOperationHandle("spp-op-" + sppConnectCalls, () -> {});
        }

        @NonNull
        @Override
        public Result<Void> prepareProfileProxy(@NonNull CbtProfileType profileType) {
            return Result.success(null);
        }

        @NonNull
        @Override
        public Result<Void> disconnectSpp(@NonNull CbtSppConnectionId connectionId) {
            return Result.success(null);
        }

        @NonNull
        @Override
        public CbtOperationHandle discover(@NonNull CbtDiscoveryConfig config, @NonNull CbtDiscoveryObserver observer) {
            return new CbtOperationHandle("disc-op", () -> {});
        }

        @NonNull
        @Override
        public Result<Void> stopDiscovery() {
            return Result.success(null);
        }

        @NonNull
        @Override
        public Result<Void> cancelBondProcess(@NonNull CbtDeviceId deviceId) {
            return Result.success(null);
        }

        @NonNull
        @Override
        public Result<Void> removeBond(@NonNull CbtDeviceId deviceId) {
            return Result.success(null);
        }

        @NonNull
        @Override
        public Result<List<CbtDeviceId>> getAclConnectedDevices() {
            return Result.success(List.of());
        }

        @NonNull
        @Override
        public CbtOperationHandle queryConnectedDevices(@NonNull CbtConnectedDeviceObserver observer) {
            return new CbtOperationHandle("query-op", () -> {});
        }

        @NonNull
        @Override
        public CbtOperationHandle discoverServices(@NonNull CbtDeviceId deviceId, @NonNull CbtServiceDiscoveryConfig config, @NonNull CbtServiceDiscoveryObserver observer) {
            return new CbtOperationHandle("sdp-op", () -> {});
        }

        @NonNull
        @Override
        public Result<CbtProfileAggregateState> getProfileAggregateState(@NonNull CbtProfileType profileType) {
            return Result.failure(null);
        }

        @NonNull
        @Override
        public Result<CbtDeviceProfileState> getDeviceProfileState(@NonNull CbtDeviceId deviceId, @NonNull CbtProfileType profileType) {
            return Result.failure(null);
        }

        @NonNull
        @Override
        public Result<CbtDeviceProfileSnapshot> getDeviceProfileSnapshot(@NonNull CbtDeviceId deviceId) {
            return Result.failure(null);
        }

        @NonNull
        @Override
        public CbtOperationHandle observeProfileState(@NonNull CbtProfileStateObserver observer) {
            return new CbtOperationHandle("prof-all-op", () -> {});
        }

        @NonNull
        @Override
        public Result<Void> disconnectSppByDevice(@NonNull CbtDeviceId deviceId) {
            return Result.success(null);
        }

        @NonNull
        @Override
        public Result<Void> disconnectAllSpp() {
            return Result.success(null);
        }

        @NonNull
        @Override
        public Result<Void> send(@NonNull CbtSppConnectionId connectionId, @NonNull byte[] data) {
            return Result.success(null);
        }

        @NonNull
        @Override
        public Result<Void> sendCommand(@NonNull CbtSppConnectionId connectionId, @NonNull CbtCommand command) {
            return Result.success(null);
        }

        @NonNull
        @Override
        public CbtOperationHandle observeCommandResult(@NonNull CbtSppConnectionId connectionId, @NonNull CbtCommandObserver observer) {
            return new CbtOperationHandle("cmd-op", () -> {});
        }

        @NonNull
        @Override
        public CbtOperationHandle observeSppState(@NonNull CbtSppConnectionId connectionId, @NonNull CbtSppStateObserver observer) {
            return new CbtOperationHandle("spp-st-op", () -> {});
        }

        @NonNull
        @Override
        public Result<List<CbtSppConnectionSnapshot>> getActiveSppConnections() {
            return Result.success(List.of());
        }

        @NonNull
        @Override
        public Result<List<CbtSppConnectionSnapshot>> getSppConnections(@NonNull CbtDeviceId deviceId) {
            return Result.success(List.of());
        }

        @NonNull
        @Override
        public Result<List<CbtDeviceSnapshot>> getDeviceSnapshots() {
            return Result.success(List.of());
        }

        @Override
        public boolean isAdapterEnabled() {
            return true;
        }

        @NonNull
        @Override
        public Result<CbtDiagnosticReport> exportDiagnosticReport() {
            return Result.failure(null);
        }

        @Override
        public void release() {
        }
    }

    @Test
    public void connect_whenClassicAlreadyConnected_directlyConnectsSpp() {
        FakeYoCbtClient client = new FakeYoCbtClient();
        client.aclConnected = true;
        FakeDelay delay = new FakeDelay();
        WyClassicRadio radio = new WyClassicRadio(client, delay);
        RecordingListener listener = new RecordingListener();

        radio.connect(TARGET_MAC, listener);

        // 已连经典蓝牙：不重复配对，也不发 connectAudio，默认只连接控制流 SPP（共 1 次）
        assertEquals(0, client.createBondCalls);
        assertEquals(0, client.connectAudioCalls);
        assertEquals(1, client.sppConnectCalls);
    }

    @Test
    public void connect_whenClassicNotConnectedAndNotBonded_createsBondThenConnectsAudioThenSpp() {
        FakeYoCbtClient client = new FakeYoCbtClient();
        client.aclConnected = false;
        client.bonded = false;
        FakeDelay delay = new FakeDelay();
        WyClassicRadio radio = new WyClassicRadio(client, delay);
        RecordingListener listener = new RecordingListener();

        radio.connect(TARGET_MAC, listener);

        // 1. 此时应当正在连接，已发起配对
        assertEquals(1, client.createBondCalls);
        assertEquals(0, client.connectAudioCalls);
        assertEquals(0, client.sppConnectCalls);
        assertNotNull(client.bondObserver);
        assertNotNull(client.aclObserver);

        // 2. 模拟系统广播收到配对成功
        client.bondObserver.onBondStateChanged(createSnapshot(CbtBondState.BONDED, CbtAclLinkState.DISCONNECTED));

        // 配对成功后应触发 connectAudio
        assertEquals(1, client.connectAudioCalls);
        assertEquals(0, client.sppConnectCalls);

        // 3. 模拟经典蓝牙 ACL 链路连通
        client.aclObserver.onAclLinkChanged(createSnapshot(CbtBondState.BONDED, CbtAclLinkState.CONNECTED));

        // 经典链路连通后，默认只连接控制流 SPP
        assertEquals(1, client.sppConnectCalls);
    }

    @Test
    public void connect_whenClassicNotConnectedButAlreadyBonded_callsConnectAudioThenSpp() {
        FakeYoCbtClient client = new FakeYoCbtClient();
        client.aclConnected = false;
        client.bonded = true;
        FakeDelay delay = new FakeDelay();
        WyClassicRadio radio = new WyClassicRadio(client, delay);
        RecordingListener listener = new RecordingListener();

        radio.connect(TARGET_MAC, listener);

        // 已配对：直接跳过配对，发起 connectAudio 建立链路
        assertEquals(0, client.createBondCalls);
        assertEquals(1, client.connectAudioCalls);
        assertEquals(0, client.sppConnectCalls);

        // 模拟 ACL 链路建立
        client.aclObserver.onAclLinkChanged(createSnapshot(CbtBondState.BONDED, CbtAclLinkState.CONNECTED));

        // 默认只连接控制流 SPP
        assertEquals(1, client.sppConnectCalls);
    }

    @Test
    public void connect_whenClassicTimeout_notifiesTimeout() {
        FakeYoCbtClient client = new FakeYoCbtClient();
        client.aclConnected = false;
        client.bonded = true;
        FakeDelay delay = new FakeDelay();
        WyClassicRadio radio = new WyClassicRadio(client, delay);
        RecordingListener listener = new RecordingListener();

        radio.connect(TARGET_MAC, listener);
        assertEquals(1, listener.controlStates.size());
        assertEquals(WyClassicRadioPort.SppState.CONNECTING, listener.controlStates.get(0));

        // 触发 15s 超时
        delay.fire();

        assertEquals(2, listener.controlStates.size());
        assertEquals(WyClassicRadioPort.SppState.FAILED, listener.controlStates.get(1));
        assertEquals(WyControl.Failure.TIMEOUT, listener.controlFailures.get(1));
    }

    @Test
    public void connect_whenBondRejected_notifiesRejected() {
        FakeYoCbtClient client = new FakeYoCbtClient();
        client.aclConnected = false;
        client.bonded = false;
        FakeDelay delay = new FakeDelay();
        WyClassicRadio radio = new WyClassicRadio(client, delay);
        RecordingListener listener = new RecordingListener();

        radio.connect(TARGET_MAC, listener);

        // 模拟配对被用户取消/拒绝
        client.bondObserver.onBondStateChanged(createSnapshot(CbtBondState.NONE, CbtAclLinkState.DISCONNECTED));

        assertEquals(2, listener.controlStates.size());
        assertEquals(WyClassicRadioPort.SppState.FAILED, listener.controlStates.get(1));
        assertEquals(WyControl.Failure.REJECTED, listener.controlFailures.get(1));
    }

    @Test
    public void getBondedDevices_returnsBondedDevicesWithConnectionState() {
        FakeYoCbtClient client = new FakeYoCbtClient();
        client.bonded = true;
        client.aclConnected = false;
        WyClassicRadio radio = new WyClassicRadio(client, new FakeDelay());

        List<WyClassicRadioPort.BondedDevice> bonded = radio.getBondedDevices();
        assertEquals(1, bonded.size());
        assertEquals("WY01", bonded.get(0).name());
        assertEquals(TARGET_MAC, bonded.get(0).address());
        assertFalse(bonded.get(0).connected());
    }

    @Test
    public void connectAudioSpp_whenConnected_connectsAudioChannel() {
        FakeYoCbtClient client = new FakeYoCbtClient();
        client.aclConnected = true;
        WyClassicRadio radio = new WyClassicRadio(client, new FakeDelay());
        RecordingListener listener = new RecordingListener();

        radio.connect(TARGET_MAC, listener);
        assertEquals(1, client.sppConnectCalls);

        // 模拟控制通道已连上
        assertFalse(client.sppObservers.isEmpty());
        client.sppObservers.get(0).onSppStateChanged(new CbtSppConnectionSnapshot(
                new CbtSppConnectionId("ctrl-conn"),
                TARGET_ID,
                CbtSppConnectionState.CONNECTED,
                CbtSppEndpoint.uuid(WyClassicRadio.CONTROL_UUID)
        ));
        assertTrue(radio.isControlConnected());

        // 请求连接音频流通道
        AtomicBoolean audioConnected = new AtomicBoolean(false);
        radio.connectAudioSpp((success, failure) -> audioConnected.set(success));

        assertEquals(2, client.sppConnectCalls);
    }
}
