package com.yovo.biz_ggec_wy.provider.lib_bluetooth;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_ggec_wy.api.WyControl;
import com.yovo.biz_ggec_wy.internal.center.port.WyClassicRadioPort;
import com.yovo.lib_bluetooth.api.YoCbtClient;
import com.yovo.lib_bluetooth.model.classic.CbtAclLinkObserver;
import com.yovo.lib_bluetooth.model.classic.CbtAclLinkState;
import com.yovo.lib_bluetooth.model.classic.CbtBondStateObserver;
import com.yovo.lib_bluetooth.model.classic.CbtCommand;
import com.yovo.lib_bluetooth.model.classic.CbtCommandObserver;
import com.yovo.lib_bluetooth.model.classic.CbtConnectedDeviceObserver;
import com.yovo.lib_bluetooth.model.classic.CbtDeviceId;
import com.yovo.lib_bluetooth.model.classic.CbtDeviceProfileSnapshot;
import com.yovo.lib_bluetooth.model.classic.CbtDeviceProfileState;
import com.yovo.lib_bluetooth.model.classic.CbtDeviceSnapshot;
import com.yovo.lib_bluetooth.model.classic.CbtDiagnosticReport;
import com.yovo.lib_bluetooth.model.classic.CbtDiscoveryConfig;
import com.yovo.lib_bluetooth.model.classic.CbtDiscoveryObserver;
import com.yovo.lib_bluetooth.model.classic.CbtErrorCode;
import com.yovo.lib_bluetooth.model.classic.CbtFailure;
import com.yovo.lib_bluetooth.model.classic.CbtLinkReadiness;
import com.yovo.lib_bluetooth.model.classic.CbtLinkSessionConfig;
import com.yovo.lib_bluetooth.model.classic.CbtLinkSessionHandle;
import com.yovo.lib_bluetooth.model.classic.CbtLinkSessionObserver;
import com.yovo.lib_bluetooth.model.classic.CbtOperationHandle;
import com.yovo.lib_bluetooth.model.classic.CbtProfileAggregateState;
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
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * WY 经典蓝牙适配的契约测试。
 *
 * <p>本测试固化的边界是：<b>链路就绪判定完全归 lib_bluetooth</b>。
 * 本类只允许消费 {@link YoCbtClient#openLinkSession} 的结果并编排两条 SPP 通道，
 * 不得自行组合 ACL/Profile 查询，也不得持有链路状态机。</p>
 */
public class WyClassicRadioTest {

    private static final String TARGET_MAC = "11:22:33:44:55:66";
    private static final CbtDeviceId TARGET_ID = CbtDeviceId.fromAddress(TARGET_MAC);

    private static final class RecordingListener implements WyClassicRadioPort.SppStateListener {
        final List<WyClassicRadioPort.SppState> controlStates = new ArrayList<>();
        final List<WyControl.Failure> controlFailures = new ArrayList<>();
        final List<WyClassicRadioPort.SppState> audioStates = new ArrayList<>();
        final List<WyControl.Failure> audioFailures = new ArrayList<>();

        @Override
        public void onControlStateChanged(@NonNull WyClassicRadioPort.SppState state,
                                          @Nullable WyControl.Failure failure) {
            controlStates.add(state);
            controlFailures.add(failure);
        }

        @Override
        public void onAudioStateChanged(@NonNull WyClassicRadioPort.SppState state,
                                        @Nullable WyControl.Failure failure) {
            audioStates.add(state);
            audioFailures.add(failure);
        }

        @Override
        public void onControlDataReceived(@NonNull byte[] data) {
        }

        @Override
        public void onAudioDataReceived(@NonNull byte[] data) {
        }
    }

    /** 可编程的假客户端：只暴露本适配真正需要的 lib 能力。 */
    private static final class FakeYoCbtClient implements YoCbtClient {

        int openLinkSessionCalls;
        int sppConnectCalls;
        int disconnectSppCalls;
        int sendCalls;

        CbtLinkSessionObserver linkObserver;
        CbtLinkSessionHandle lastSession;
        boolean sessionCancelled;

        final List<CbtSppEndpoint> requestedEndpoints = new ArrayList<>();
        final List<CbtSppConnectionObserver> sppObservers = new ArrayList<>();

        @NonNull
        @Override
        public CbtLinkSessionHandle openLinkSession(@NonNull CbtDeviceId deviceId,
                                                    @NonNull CbtLinkSessionConfig config,
                                                    @NonNull CbtLinkSessionObserver observer) {
            openLinkSessionCalls++;
            linkObserver = observer;
            lastSession = new CbtLinkSessionHandle() {
                private boolean active = true;

                @Override
                public void cancel() {
                    active = false;
                    sessionCancelled = true;
                }

                @Override
                public boolean isActive() {
                    return active;
                }
            };
            return lastSession;
        }

        @NonNull
        @Override
        public CbtOperationHandle connectSpp(@NonNull CbtDeviceId deviceId,
                                             @NonNull CbtSppEndpoint endpoint,
                                             @NonNull CbtSppConfig config,
                                             @NonNull CbtSppConnectionObserver observer) {
            sppConnectCalls++;
            requestedEndpoints.add(endpoint);
            sppObservers.add(observer);
            return noopHandle();
        }

        @NonNull
        @Override
        public Result<Void> send(@NonNull CbtSppConnectionId connectionId, @NonNull byte[] data) {
            sendCalls++;
            return Result.success(null);
        }

        @NonNull
        @Override
        public Result<Void> disconnectSpp(@NonNull CbtSppConnectionId connectionId) {
            disconnectSppCalls++;
            return Result.success(null);
        }

        /** 模拟 lib 上报就绪。 */
        void emitReady() {
            if (linkObserver != null) {
                linkObserver.onReadinessChanged(TARGET_ID, CbtLinkReadiness.READY);
            }
        }

        /** 模拟 SPP 通道状态。 */
        void emitSpp(int index, @NonNull CbtSppConnectionState state) {
            CbtSppConnectionObserver observer = sppObservers.get(index);
            observer.onSppStateChanged(snapshot(state));
        }

        /** 模拟 SPP 通道以失败/带原因的终态上报（如 LOCAL_CLOSED / REMOTE_CLOSED）。 */
        void emitSppFailure(int index, @NonNull CbtSppConnectionState state,
                            @NonNull CbtErrorCode errorCode) {
            CbtSppConnectionObserver observer = sppObservers.get(index);
            CbtSppConnectionSnapshot snap = new CbtSppConnectionSnapshot(
                    new CbtSppConnectionId("spp-test"),
                    TARGET_ID,
                    state,
                    CbtSppEndpoint.uuid(WyClassicRadio.audioUuid()),
                    new CbtFailure(errorCode, errorCode.name(), null, true, true, "test", TARGET_ID),
                    -1);
            observer.onSppStateChanged(snap);
        }

        private static CbtSppConnectionSnapshot snapshot(@NonNull CbtSppConnectionState state) {
            return new CbtSppConnectionSnapshot(
                    new CbtSppConnectionId("spp-test"),
                    TARGET_ID,
                    state,
                    CbtSppEndpoint.uuid(WyClassicRadio.controlUuid()));
        }

        private static CbtOperationHandle noopHandle() {
            return new CbtOperationHandle("noop", () -> { });
        }

        // ── 本适配不使用的 lib 能力：保持显式未实现，避免误用 ──────────

        @NonNull
        @Override
        public CbtOperationHandle discover(@NonNull CbtDiscoveryConfig config,
                                           @NonNull CbtDiscoveryObserver observer) {
            throw new UnsupportedOperationException("本适配不直接发起发现");
        }

        @NonNull
        @Override
        public Result<Void> stopDiscovery() {
            return Result.success(null);
        }

        @NonNull
        @Override
        public Result<List<CbtDeviceSnapshot>> getBondedDevices() {
            return Result.success(List.of());
        }

        @NonNull
        @Override
        public Result<Void> createBond(@NonNull CbtDeviceId deviceId) {
            throw new UnsupportedOperationException("链路就绪归 lib，本适配不得自行配对");
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
        public CbtOperationHandle observeBondState(@NonNull CbtBondStateObserver observer) {
            throw new UnsupportedOperationException("链路就绪归 lib，本适配不得自行监听配对");
        }

        @NonNull
        @Override
        public CbtOperationHandle observeAclLinkState(@NonNull CbtAclLinkObserver observer) {
            throw new UnsupportedOperationException("链路就绪归 lib，本适配不得自行监听 ACL");
        }

        @NonNull
        @Override
        public Result<CbtAclLinkState> getAclState(@NonNull CbtDeviceId deviceId) {
            throw new UnsupportedOperationException("链路就绪归 lib，本适配不得自行查询 ACL");
        }

        @NonNull
        @Override
        public Result<List<CbtDeviceId>> getAclConnectedDevices() {
            return Result.success(List.of());
        }

        @NonNull
        @Override
        public CbtOperationHandle queryConnectedDevices(
                @NonNull CbtConnectedDeviceObserver observer) {
            throw new UnsupportedOperationException("链路就绪归 lib，本适配不得自行查询已连接设备");
        }

        @NonNull
        @Override
        public CbtOperationHandle discoverServices(@NonNull CbtDeviceId deviceId,
                                                   @NonNull CbtServiceDiscoveryConfig config,
                                                   @NonNull CbtServiceDiscoveryObserver observer) {
            return noopHandle();
        }

        @NonNull
        @Override
        public Result<Void> prepareProfileProxy(@NonNull CbtProfileType profileType) {
            throw new UnsupportedOperationException("链路就绪归 lib，本适配不得自行准备代理");
        }

        @NonNull
        @Override
        public Result<Void> connectAudio(@NonNull CbtDeviceId deviceId) {
            throw new UnsupportedOperationException("链路就绪归 lib，本适配不得自行连音频");
        }

        @NonNull
        @Override
        public Result<CbtProfileAggregateState> getProfileAggregateState(
                @NonNull CbtProfileType profileType) {
            throw new UnsupportedOperationException("链路就绪归 lib");
        }

        @NonNull
        @Override
        public Result<CbtDeviceProfileState> getDeviceProfileState(
                @NonNull CbtDeviceId deviceId, @NonNull CbtProfileType profileType) {
            throw new UnsupportedOperationException("链路就绪归 lib");
        }

        @NonNull
        @Override
        public Result<CbtDeviceProfileSnapshot> getDeviceProfileSnapshot(
                @NonNull CbtDeviceId deviceId) {
            throw new UnsupportedOperationException("链路就绪归 lib");
        }

        @NonNull
        @Override
        public Result<List<CbtProfileType>> getConnectedProfiles(
                @NonNull CbtDeviceId deviceId) {
            throw new UnsupportedOperationException("链路就绪归 lib");
        }

        @NonNull
        @Override
        public CbtOperationHandle observeProfileState(@NonNull CbtDeviceId deviceId,
                                                      @NonNull CbtProfileStateObserver observer) {
            throw new UnsupportedOperationException("链路就绪归 lib");
        }

        @NonNull
        @Override
        public CbtOperationHandle observeProfileState(
                @NonNull CbtProfileStateObserver observer) {
            throw new UnsupportedOperationException("链路就绪归 lib");
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
        public Result<Void> sendCommand(@NonNull CbtSppConnectionId connectionId,
                                        @NonNull CbtCommand command) {
            return Result.success(null);
        }

        @NonNull
        @Override
        public CbtOperationHandle observeCommandResult(
                @NonNull CbtSppConnectionId connectionId,
                @NonNull CbtCommandObserver observer) {
            return noopHandle();
        }

        @NonNull
        @Override
        public CbtOperationHandle observeSppState(
                @NonNull CbtSppConnectionId connectionId,
                @NonNull CbtSppStateObserver observer) {
            return noopHandle();
        }

        @NonNull
        @Override
        public Result<List<CbtSppConnectionSnapshot>> getActiveSppConnections() {
            return Result.success(List.of());
        }

        @NonNull
        @Override
        public Result<List<CbtSppConnectionSnapshot>> getSppConnections(
                @NonNull CbtDeviceId deviceId) {
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
            throw new UnsupportedOperationException("诊断不属于本适配");
        }

        @Override
        public void release() {
        }
    }

    // ── 契约：链路就绪归 lib ──────────────────────────────────────

    @Test
    public void connect_opensLinkSessionAndDoesNotQueryAclItself() {
        FakeYoCbtClient client = new FakeYoCbtClient();
        WyClassicRadio radio = new WyClassicRadio(client);
        RecordingListener listener = new RecordingListener();

        radio.connect(TARGET_MAC, listener);

        // 必须走 lib 的链路会话；不得自行查询/编排链路。
        assertEquals(1, client.openLinkSessionCalls);
        assertEquals(0, client.sppConnectCalls);
        assertEquals(List.of(WyClassicRadioPort.SppState.CONNECTING),
                listener.controlStates);
    }

    @Test
    public void connect_whenLibReportsReady_connectsControlChannelOnly() {
        FakeYoCbtClient client = new FakeYoCbtClient();
        WyClassicRadio radio = new WyClassicRadio(client);
        RecordingListener listener = new RecordingListener();

        radio.connect(TARGET_MAC, listener);
        client.emitReady();

        // 只连控制流；音频流按需另建。
        assertEquals(1, client.sppConnectCalls);
        assertEquals(1, client.requestedEndpoints.size());
        assertEquals(WyClassicRadio.controlUuid(),
                client.requestedEndpoints.get(0).uuid());
    }

    @Test
    public void controlChannelConnected_marksControlConnected() {
        FakeYoCbtClient client = new FakeYoCbtClient();
        WyClassicRadio radio = new WyClassicRadio(client);
        RecordingListener listener = new RecordingListener();

        radio.connect(TARGET_MAC, listener);
        client.emitReady();
        client.emitSpp(0, CbtSppConnectionState.CONNECTED);

        assertTrue(radio.isControlConnected());
        assertFalse(radio.isAudioConnected());
        assertTrue(listener.controlStates.contains(WyClassicRadioPort.SppState.CONNECTED));
    }

    @Test
    public void connectAudioSpp_requiresControlChannelFirst() {
        FakeYoCbtClient client = new FakeYoCbtClient();
        WyClassicRadio radio = new WyClassicRadio(client);
        RecordingListener listener = new RecordingListener();
        radio.connect(TARGET_MAC, listener);
        client.emitReady();

        boolean[] result = new boolean[1];
        WyControl.Failure[] failure = new WyControl.Failure[1];
        radio.connectAudioSpp((success, f) -> {
            result[0] = success;
            failure[0] = f;
        });

        // 控制流未连通时不得建立音频流。
        assertFalse(result[0]);
        assertEquals(WyControl.Failure.DISCONNECTED, failure[0]);
        assertEquals(1, client.sppConnectCalls);
    }

    @Test
    public void connectAudioSpp_afterControlConnected_opensAudioEndpoint() {
        FakeYoCbtClient client = new FakeYoCbtClient();
        WyClassicRadio radio = new WyClassicRadio(client);
        RecordingListener listener = new RecordingListener();
        radio.connect(TARGET_MAC, listener);
        client.emitReady();
        client.emitSpp(0, CbtSppConnectionState.CONNECTED);

        radio.connectAudioSpp((success, f) -> { });

        assertEquals(2, client.sppConnectCalls);
        assertEquals(WyClassicRadio.audioUuid(),
                client.requestedEndpoints.get(1).uuid());
    }

    @Test
    public void sendControl_whenNotConnected_failsWithDisconnected() {
        FakeYoCbtClient client = new FakeYoCbtClient();
        WyClassicRadio radio = new WyClassicRadio(client);

        boolean[] success = new boolean[1];
        WyControl.Failure[] failure = new WyControl.Failure[1];
        radio.sendControl(new byte[]{0x01}, (s, f) -> {
            success[0] = s;
            failure[0] = f;
        });

        assertFalse(success[0]);
        assertEquals(WyControl.Failure.DISCONNECTED, failure[0]);
        assertEquals(0, client.sendCalls);
    }

    @Test
    public void disconnect_cancelsLinkSession() {
        FakeYoCbtClient client = new FakeYoCbtClient();
        WyClassicRadio radio = new WyClassicRadio(client);
        RecordingListener listener = new RecordingListener();
        radio.connect(TARGET_MAC, listener);

        radio.disconnect();

        assertTrue(client.sessionCancelled);
    }

    @Test
    public void release_isIdempotentAndCancelsSession() {
        FakeYoCbtClient client = new FakeYoCbtClient();
        WyClassicRadio radio = new WyClassicRadio(client);
        radio.connect(TARGET_MAC, new RecordingListener());

        radio.release();
        radio.release();

        assertTrue(client.sessionCancelled);
    }

    @Test
    public void linkFailureFromLib_isMappedToFailureState() {
        FakeYoCbtClient client = new FakeYoCbtClient();
        WyClassicRadio radio = new WyClassicRadio(client);
        RecordingListener listener = new RecordingListener();
        radio.connect(TARGET_MAC, listener);

        assertNotNull(client.linkObserver);
        client.linkObserver.onLinkFailed(TARGET_ID,
                new com.yovo.lib_bluetooth.model.classic.CbtFailure(
                        com.yovo.lib_bluetooth.model.classic.CbtErrorCode.LINK_READINESS_TIMEOUT,
                        "timeout", null, true, true, "test", TARGET_ID));

        assertEquals(WyClassicRadioPort.SppState.FAILED,
                listener.controlStates.get(listener.controlStates.size() - 1));
        assertEquals(WyControl.Failure.TIMEOUT,
                listener.controlFailures.get(listener.controlFailures.size() - 1));
    }

    // ── WS-C：主动关闭音频通道不得被误报成 DEVICE_ERROR ──────────────

    private static void connectControlAndAudio(FakeYoCbtClient client,
                                               WyClassicRadio radio,
                                               RecordingListener listener,
                                               boolean[] audioConnected) {
        radio.connect(TARGET_MAC, listener);
        client.emitReady();
        client.emitSpp(0, CbtSppConnectionState.CONNECTED);
        radio.connectAudioSpp((success, f) -> audioConnected[0] = success);
        client.emitSpp(1, CbtSppConnectionState.CONNECTED);
    }

    @Test
    public void localAudioClose_isNotReportedAsDeviceError() {
        FakeYoCbtClient client = new FakeYoCbtClient();
        WyClassicRadio radio = new WyClassicRadio(client);
        RecordingListener listener = new RecordingListener();
        boolean[] audioConnected = new boolean[1];
        connectControlAndAudio(client, radio, listener, audioConnected);
        assertTrue(audioConnected[0]);
        assertTrue(radio.isAudioConnected());

        // 本地主动关闭音频通道，lib 随后以 DISCONNECTED + LOCAL_CLOSED 广播该主动关闭。
        radio.disconnectAudioSpp();
        client.emitSppFailure(1, CbtSppConnectionState.DISCONNECTED,
                CbtErrorCode.SPP_LOCAL_CLOSED);

        assertEquals(WyClassicRadioPort.SppState.DISCONNECTED,
                listener.audioStates.get(listener.audioStates.size() - 1));
        // 关键断言：正常收尾的 failure 必须为 null，而非 DEVICE_ERROR。
        assertNull(listener.audioFailures.get(listener.audioFailures.size() - 1));
        assertFalse(radio.isAudioConnected());
    }

    @Test
    public void remoteAudioClose_mapsToDisconnected() {
        FakeYoCbtClient client = new FakeYoCbtClient();
        WyClassicRadio radio = new WyClassicRadio(client);
        RecordingListener listener = new RecordingListener();
        boolean[] audioConnected = new boolean[1];
        connectControlAndAudio(client, radio, listener, audioConnected);

        // 非本地主动关闭（远端关闭）应映射为 DISCONNECTED，而不是 DEVICE_ERROR。
        client.emitSppFailure(1, CbtSppConnectionState.DISCONNECTED,
                CbtErrorCode.SPP_REMOTE_CLOSED);

        assertEquals(WyClassicRadioPort.SppState.DISCONNECTED,
                listener.audioStates.get(listener.audioStates.size() - 1));
        assertEquals(WyControl.Failure.DISCONNECTED,
                listener.audioFailures.get(listener.audioFailures.size() - 1));
    }
}
