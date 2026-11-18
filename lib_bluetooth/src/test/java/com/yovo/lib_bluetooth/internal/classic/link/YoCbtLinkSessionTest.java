package com.yovo.lib_bluetooth.internal.classic.link;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.api.YoCbtClient;
import com.yovo.lib_bluetooth.internal.classic.runtime.YoCbtLinkRuntime;
import com.yovo.lib_bluetooth.internal.common.concurrency.YoTimeoutHandle;
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
import com.yovo.lib_bluetooth.model.classic.CbtSppEndpoint;
import com.yovo.lib_bluetooth.model.classic.CbtSppStateObserver;
import com.yovo.lib_bluetooth.model.common.Result;

import org.junit.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link YoCbtLinkSession} 的状态机合同（确定性、无线程、无平台依赖）。
 *
 * <p>用「同步假运行时 + 手动定时器」把真实线程与 Looper 排除在外，
 * 从而稳定地钉死四条最关键的行为：</p>
 * <ol>
 *   <li>先查后等：链路已连接时直接 READY，<b>不发起任何 connectAudio</b>；</li>
 *   <li>明确断开（DOWN）时才发起 connectAudio；</li>
 *   <li>超时收敛为 onLinkFailed；</li>
 *   <li>失败/取消即终态：之后的事件不再推进，回调只发生一次。</li>
 * </ol>
 */
public final class YoCbtLinkSessionTest {

    private static final String MAC = "11:22:33:44:55:66";
    private static final CbtDeviceId ID = CbtDeviceId.fromAddress(MAC);

    // ── 假运行时：state/callback 同步执行，schedule 记录任务由测试手动触发 ──
    private static final class FakeRuntime implements YoCbtLinkRuntime {

        private static final class Task implements YoTimeoutHandle {
            final Runnable runnable;
            final long delayMillis;
            boolean cancelled;

            Task(@NonNull Runnable runnable, long delayMillis) {
                this.runnable = runnable;
                this.delayMillis = delayMillis;
            }

            @Override
            public void cancel() {
                cancelled = true;
            }

            void fire() {
                if (!cancelled) {
                    runnable.run();
                }
            }
        }

        final List<Task> tasks = new ArrayList<>();

        @Override
        public void state(@NonNull Runnable task) {
            task.run();
        }

        @Override
        public void callback(@NonNull Runnable task) {
            task.run();
        }

        @NonNull
        @Override
        public YoTimeoutHandle schedule(@NonNull Runnable task, long delayMillis) {
            Task t = new Task(task, delayMillis);
            tasks.add(t);
            return t;
        }

        @Nullable
        Task firstActiveWithDelay(long delayMillis) {
            for (Task t : tasks) {
                if (t.delayMillis == delayMillis && !t.cancelled) {
                    return t;
                }
            }
            return null;
        }
    }

    private static final class RecordingObserver implements CbtLinkSessionObserver {
        final List<CbtLinkReadiness> readiness = new ArrayList<>();
        int failures;
        int cancelled;

        @Override
        public void onReadinessChanged(@NonNull CbtDeviceId deviceId,
                                       @NonNull CbtLinkReadiness r) {
            readiness.add(r);
        }

        @Override
        public void onLinkFailed(@NonNull CbtDeviceId deviceId, @NonNull CbtFailure failure) {
            failures++;
        }

        @Override
        public void onLinkCancelled(@NonNull CbtDeviceId deviceId) {
            cancelled++;
        }

        CbtLinkReadiness last() {
            return readiness.isEmpty() ? null : readiness.get(readiness.size() - 1);
        }
    }

    private static CbtDeviceSnapshot snapshot(@NonNull CbtBondState bond,
                                              @NonNull CbtAclLinkState acl) {
        return new CbtDeviceSnapshot(
                new CbtDeviceIdentity(ID, MAC, "WY01"),
                bond, acl, -50, 0L, 0L,
                List.of(), new EnumMap<>(CbtProfileType.class), List.of(), List.of());
    }

    /** 只实现会话真正用到的方法；其余抛 UnsupportedOperationException，避免误用。 */
    private static final class FakeClient implements YoCbtClient {
        CbtAclLinkState aclState = CbtAclLinkState.DISCONNECTED;
        List<CbtProfileType> connectedProfiles = List.of();
        boolean bonded = true;
        List<CbtDeviceSnapshot> queryResult = List.of();
        /** 非 null 时模拟「查询失败」，即事实不足（UNKNOWN 语义），而非「查询成功但设备不在」。 */
        @Nullable
        CbtFailure queryFailure;
        int connectAudioCalls;
        int createBondCalls;
        boolean adapterEnabled = true;
        int queryCalls;

        CbtAclLinkObserver aclObserver;

        @NonNull
        @Override
        public CbtOperationHandle queryConnectedDevices(
                @NonNull CbtConnectedDeviceObserver observer) {
            queryCalls++;
            if (queryFailure != null) {
                observer.onConnectedDeviceQueryFailed(queryFailure);
            } else {
                observer.onConnectedDevices(queryResult);
            }
            return noop();
        }

        @NonNull
        @Override
        public Result<CbtAclLinkState> getAclState(@NonNull CbtDeviceId deviceId) {
            return Result.success(aclState);
        }

        @NonNull
        @Override
        public Result<List<CbtProfileType>> getConnectedProfiles(@NonNull CbtDeviceId deviceId) {
            return Result.success(connectedProfiles);
        }

        @NonNull
        @Override
        public Result<CbtDeviceProfileSnapshot> getDeviceProfileSnapshot(
                @NonNull CbtDeviceId deviceId) {
            return Result.success(null);
        }

        @NonNull
        @Override
        public Result<List<CbtDeviceSnapshot>> getBondedDevices() {
            return Result.success(List.of(snapshot(bonded ? CbtBondState.BONDED : CbtBondState.NONE,
                    CbtAclLinkState.UNKNOWN)));
        }

        @NonNull
        @Override
        public Result<Void> connectAudio(@NonNull CbtDeviceId deviceId) {
            connectAudioCalls++;
            return Result.success(null);
        }

        @NonNull
        @Override
        public Result<Void> createBond(@NonNull CbtDeviceId deviceId) {
            createBondCalls++;
            return Result.success(null);
        }

        @NonNull
        @Override
        public Result<Void> prepareProfileProxy(@NonNull CbtProfileType profileType) {
            return Result.success(null);
        }

        @NonNull
        @Override
        public CbtOperationHandle observeAclLinkState(@NonNull CbtAclLinkObserver observer) {
            aclObserver = observer;
            return noop();
        }

        @NonNull
        @Override
        public CbtOperationHandle observeProfileState(@NonNull CbtDeviceId deviceId,
                                                      @NonNull CbtProfileStateObserver observer) {
            return noop();
        }

        @NonNull
        @Override
        public CbtOperationHandle observeBondState(@NonNull CbtBondStateObserver observer) {
            return noop();
        }

        private static CbtOperationHandle noop() {
            return new CbtOperationHandle("noop", () -> { });
        }

        // ── 会话不使用，显式不支持 ──────────────────────────────────
        @NonNull
        @Override
        public CbtOperationHandle discover(@NonNull CbtDiscoveryConfig config,
                                           @NonNull CbtDiscoveryObserver observer) {
            throw new UnsupportedOperationException();
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
        public CbtOperationHandle discoverServices(@NonNull CbtDeviceId deviceId,
                                                   @NonNull CbtServiceDiscoveryConfig config,
                                                   @NonNull CbtServiceDiscoveryObserver observer) {
            throw new UnsupportedOperationException();
        }

        @NonNull
        @Override
        public Result<CbtProfileAggregateState> getProfileAggregateState(
                @NonNull CbtProfileType profileType) {
            throw new UnsupportedOperationException();
        }

        @NonNull
        @Override
        public Result<CbtDeviceProfileState> getDeviceProfileState(
                @NonNull CbtDeviceId deviceId, @NonNull CbtProfileType profileType) {
            throw new UnsupportedOperationException();
        }

        @NonNull
        @Override
        public CbtOperationHandle observeProfileState(
                @NonNull CbtProfileStateObserver observer) {
            return noop();
        }

        @NonNull
        @Override
        public CbtOperationHandle connectSpp(@NonNull CbtDeviceId deviceId,
                                             @NonNull CbtSppEndpoint endpoint,
                                             @NonNull CbtSppConfig config,
                                             @NonNull CbtSppConnectionObserver observer) {
            throw new UnsupportedOperationException();
        }

        @NonNull
        @Override
        public CbtLinkSessionHandle openLinkSession(@NonNull CbtDeviceId deviceId,
                                                    @NonNull CbtLinkSessionConfig config,
                                                    @NonNull CbtLinkSessionObserver observer) {
            throw new UnsupportedOperationException();
        }

        @NonNull
        @Override
        public Result<Void> disconnectSpp(@NonNull CbtSppConnectionId connectionId) {
            return Result.success(null);
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
        public Result<Void> send(@NonNull CbtSppConnectionId connectionId,
                                 @NonNull byte[] data) {
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
            return noop();
        }

        @NonNull
        @Override
        public CbtOperationHandle observeSppState(@NonNull CbtSppConnectionId connectionId,
                                                  @NonNull CbtSppStateObserver observer) {
            return noop();
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
            return adapterEnabled;
        }

        @NonNull
        @Override
        public Result<CbtDiagnosticReport> exportDiagnosticReport() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void release() {
        }
    }

    private static CbtLinkSessionConfig fastConfig() {
        return CbtLinkSessionConfig.builder()
                .readinessTimeoutMillis(1_000L)
                .queryRetryMillis(500L)
                .build();
    }

    // ── 用例 ─────────────────────────────────────────────────────

    @Test
    public void aclAlreadyConnected_becomesReadyWithoutAnyConnectRequest() {
        FakeClient client = new FakeClient();
        client.aclState = CbtAclLinkState.CONNECTED;
        FakeRuntime rt = new FakeRuntime();
        RecordingObserver obs = new RecordingObserver();

        new YoCbtLinkSession(client, rt, ID, fastConfig(), obs).start();

        assertEquals(CbtLinkReadiness.READY, obs.last());
        assertEquals("先查后等：已连接不得再发 connectAudio", 0, client.connectAudioCalls);
        assertEquals(0, client.createBondCalls);
    }

    @Test
    public void connectedProfilesNonEmpty_becomesReady() {
        FakeClient client = new FakeClient();
        client.aclState = CbtAclLinkState.UNKNOWN;
        client.connectedProfiles = List.of(CbtProfileType.A2DP);
        FakeRuntime rt = new FakeRuntime();
        RecordingObserver obs = new RecordingObserver();

        new YoCbtLinkSession(client, rt, ID, fastConfig(), obs).start();

        assertEquals(CbtLinkReadiness.READY, obs.last());
        assertEquals(0, client.connectAudioCalls);
    }

    @Test
    public void downState_requestsAudioExactlyOnce() {
        FakeClient client = new FakeClient();
        client.aclState = CbtAclLinkState.DISCONNECTED;
        client.connectedProfiles = List.of();
        client.bonded = true;
        client.queryResult = List.of(); // 系统查询确认不在已连接集合
        FakeRuntime rt = new FakeRuntime();
        RecordingObserver obs = new RecordingObserver();

        new YoCbtLinkSession(client, rt, ID, fastConfig(), obs).start();

        assertEquals(CbtLinkReadiness.DOWN, obs.last());
        assertEquals("DOWN 时发起一次 connectAudio", 1, client.connectAudioCalls);
        assertEquals("已配对，不应再配对", 0, client.createBondCalls);
    }

    @Test
    public void unknownNeverRequestsConnect() {
        FakeClient client = new FakeClient();
        client.aclState = CbtAclLinkState.UNKNOWN;
        client.connectedProfiles = null; // 事实不足
        client.queryFailure = new CbtFailure(CbtErrorCode.ACL_QUERY_FAILED,
                "查询失败", null, true, true, "test", ID); // 查询失败 → 保持 UNKNOWN
        FakeRuntime rt = new FakeRuntime();
        RecordingObserver obs = new RecordingObserver();

        new YoCbtLinkSession(client, rt, ID, fastConfig(), obs).start();

        assertEquals(CbtLinkReadiness.UNKNOWN, obs.last());
        assertEquals("UNKNOWN 绝不据此连接", 0, client.connectAudioCalls);
        assertEquals(0, client.createBondCalls);
    }

    @Test
    public void querySucceededButDeviceAbsent_isTreatedAsDisconnectedAndConnects() {
        // 与上一条对照：查询成功、但设备不在已连接集合 → 视为断开，可安全发起连接。
        FakeClient client = new FakeClient();
        client.aclState = CbtAclLinkState.UNKNOWN;
        client.connectedProfiles = null;
        client.bonded = true;
        client.queryResult = List.of(); // 成功但为空
        FakeRuntime rt = new FakeRuntime();
        RecordingObserver obs = new RecordingObserver();

        new YoCbtLinkSession(client, rt, ID, fastConfig(), obs).start();

        assertEquals("查询确认不在已连接集合 → 连接一次", 1, client.connectAudioCalls);
    }

    @Test
    public void timeout_reportsFailureOnce() {
        FakeClient client = new FakeClient();
        client.aclState = CbtAclLinkState.DISCONNECTED;
        client.connectedProfiles = List.of();
        client.bonded = true;
        FakeRuntime rt = new FakeRuntime();
        RecordingObserver obs = new RecordingObserver();

        new YoCbtLinkSession(client, rt, ID, fastConfig(), obs).start();

        FakeRuntime.Task timeout = rt.firstActiveWithDelay(1_000L);
        assertNotNull("应安排了就绪超时任务", timeout);
        timeout.fire();

        assertEquals(1, obs.failures);
    }

    @Test
    public void afterFailure_lateEventsDoNotAdvanceOrDoubleReport() {
        FakeClient client = new FakeClient();
        client.aclState = CbtAclLinkState.DISCONNECTED;
        client.connectedProfiles = List.of();
        client.bonded = true;
        FakeRuntime rt = new FakeRuntime();
        RecordingObserver obs = new RecordingObserver();
        YoCbtLinkSession session = new YoCbtLinkSession(client, rt, ID, fastConfig(), obs);
        session.start();

        rt.firstActiveWithDelay(1_000L).fire();
        assertEquals(1, obs.failures);

        // 失败后即便设备“连上”并送达 ACL 事件，会话已是终态，不得推进/二次上报。
        client.aclState = CbtAclLinkState.CONNECTED;
        if (client.aclObserver != null) {
            client.aclObserver.onAclLinkChanged(snapshot(CbtBondState.BONDED,
                    CbtAclLinkState.CONNECTED));
        }

        assertFalse(session.isActive());
        assertEquals("失败只回调一次", 1, obs.failures);
        assertFalse("失败后不得再报 READY", obs.readiness.contains(CbtLinkReadiness.READY));
    }

    @Test
    public void failsFastWhenAdapterDisabled() {
        FakeClient client = new FakeClient();
        client.adapterEnabled = false;
        FakeRuntime rt = new FakeRuntime();
        RecordingObserver obs = new RecordingObserver();

        YoCbtLinkSession session = new YoCbtLinkSession(client, rt, ID, fastConfig(), obs);
        session.start();

        assertEquals("适配器关闭应一次性失败", 1, obs.failures);
        assertFalse(session.isActive());
        assertEquals("失败快于查询：不应发起系统查询", 0, client.queryCalls);
        assertEquals(0, client.connectAudioCalls);
    }

    @Test
    public void cancel_isIdempotentAndSingleCallback() {
        FakeClient client = new FakeClient();
        client.aclState = CbtAclLinkState.UNKNOWN;
        client.connectedProfiles = null;
        FakeRuntime rt = new FakeRuntime();
        RecordingObserver obs = new RecordingObserver();
        YoCbtLinkSession session = new YoCbtLinkSession(client, rt, ID, fastConfig(), obs);
        session.start();

        session.cancel();
        session.cancel();

        assertFalse(session.isActive());
        assertEquals(1, obs.cancelled);
        assertEquals(0, obs.failures);
    }
}
