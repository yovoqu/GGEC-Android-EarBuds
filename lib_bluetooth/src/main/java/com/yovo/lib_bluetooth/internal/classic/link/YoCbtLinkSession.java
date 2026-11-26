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
import com.yovo.lib_bluetooth.model.classic.CbtConnectedDeviceObserver;
import com.yovo.lib_bluetooth.model.classic.CbtDeviceId;
import com.yovo.lib_bluetooth.model.classic.CbtDeviceProfileSnapshot;
import com.yovo.lib_bluetooth.model.classic.CbtDeviceProfileState;
import com.yovo.lib_bluetooth.model.classic.CbtDeviceSnapshot;
import com.yovo.lib_bluetooth.model.classic.CbtErrorCode;
import com.yovo.lib_bluetooth.model.classic.CbtFailure;
import com.yovo.lib_bluetooth.model.classic.CbtLinkReadiness;
import com.yovo.lib_bluetooth.model.classic.CbtLinkSessionConfig;
import com.yovo.lib_bluetooth.model.classic.CbtLinkSessionHandle;
import com.yovo.lib_bluetooth.model.classic.CbtLinkSessionObserver;
import com.yovo.lib_bluetooth.model.classic.CbtOperationHandle;
import com.yovo.lib_bluetooth.model.classic.CbtProfileConnectionState;
import com.yovo.lib_bluetooth.model.classic.CbtProfileStateObserver;
import com.yovo.lib_bluetooth.model.classic.CbtProfileType;
import com.yovo.lib_bluetooth.model.common.Result;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 经典蓝牙「链路就绪」会话：把配对 → ACL → 音频 Profile → 就绪 的完整编排收敛为库内唯一实现。
 *
 * <h3>为什么这个能力必须在库里</h3>
 * <p>链路就绪判定依赖三条平台事实（ACL 状态、Profile 连接列表、Profile 快照），
 * 而它们各自可能返回「未知」。让每个上层各写一遍，必然出现两类缺陷：</p>
 * <ol>
 *   <li><b>把未知当断开</b>：设备其实已连上，重复 {@code connectAudio} 被系统忽略
 *       （{@code A2dpStateMachine: CONNECT ignored}），不产生任何状态广播，
 *       只监听事件的调用方永远等不到回调，最终超时；</li>
 *   <li><b>只监听不查询</b>：链路在订阅之前就已建立，事件永远不会再来。</li>
 * </ol>
 * <p>本会话的解法对齐业界成熟做法（Nordic BleManager 的
 * 「连接即查询 + 事件驱动 + 显式超时」模型）：</p>
 * <ul>
 *   <li><b>先查后等</b>：进入会话立刻做一次主动查询，命中即 READY，不发起任何请求；</li>
 *   <li><b>三态而非布尔</b>：{@link CbtLinkReadiness#UNKNOWN} 表示事实不足，
 *       只继续等待，绝不据此配对或重连；</li>
 *   <li><b>周期复核</b>：UNKNOWN 期间按 {@code queryRetryMillis} 主动复核，
 *       覆盖「事件已错过」的窗口；</li>
 *   <li><b>显式超时</b>：到点即 {@code onLinkFailed}，不留悬挂等待。</li>
 * </ul>
 *
 * <h3>线程与生命周期</h3>
 * <p>所有状态字段只在 {@link YoCbtLinkRuntime} 的 state 线程改写；平台回调经
 * {@code runtime.state(...)} 回到 state 线程后再推进状态机。
 * {@link #cancel()} 幂等，取消后不再有任何回调。</p>
 */
public final class YoCbtLinkSession implements CbtLinkSessionHandle {

    private static final String TAG = "YoCbtLinkSession";

    private final YoCbtClient client;
    private final YoCbtLinkRuntime runtime;
    private final CbtDeviceId deviceId;
    private final CbtLinkSessionConfig config;
    private final CbtLinkSessionObserver observer;
    private final AtomicBoolean active = new AtomicBoolean(true);

    @Nullable
    private CbtOperationHandle aclWatch;
    @Nullable
    private CbtOperationHandle profileWatch;
    @Nullable
    private CbtOperationHandle bondWatch;
    @Nullable
    private CbtOperationHandle queryHandle;
    @Nullable
    private YoTimeoutHandle timeoutHandle;
    @Nullable
    private YoTimeoutHandle retryHandle;

    /** 是否有一轮系统查询在途，避免并发重复查询。 */
    private boolean querying;
    /** 锁：会话状态字段的互斥（state 线程为主，但查询回调跨线程）。 */
    private final Object lock = new Object();

    /** 上次上报的就绪事实；用于「只在变化时回调」。 */
    @Nullable
    private CbtLinkReadiness lastReported;
    /** 是否已经发起过 connectAudio，避免重复请求。 */
    private boolean audioRequested;
    /** 是否已经发起过 createBond。 */
    private boolean bondRequested;
    /** 是否正处于配对进行中（BONDING）；配对完成前不得抢跑 connectAudio。 */
    private boolean bondingInProgress;
    /** 是否曾经到达 READY。到达后本会话只负责上报事实变化，不再自动驱动重连。 */
    private boolean everReady;

    private enum BondFact {
        NONE,
        BONDING,
        BONDED,
        UNKNOWN
    }

    public YoCbtLinkSession(@NonNull YoCbtClient client,
                            @NonNull YoCbtLinkRuntime runtime,
                            @NonNull CbtDeviceId deviceId,
                            @NonNull CbtLinkSessionConfig config,
                            @NonNull CbtLinkSessionObserver observer) {
        this.client = client;
        this.runtime = runtime;
        this.deviceId = deviceId;
        this.config = config;
        this.observer = observer;
    }

    /** 启动会话：订阅事实源 → 主动查询 → 按需发起连接 → 等待/超时。 */
    public void start() {
        runtime.state(this::beginOnStateThread);
    }

    private void beginOnStateThread() {
        if (!active.get()) {
            return;
        }
        // 经典蓝牙发现会干扰后续 createBond / SDP / 音频与 SPP 连接，进入链路就绪前先停止发现。
        client.stopDiscovery();

        // 1. 订阅三条事实源，覆盖「后续变化」。
        aclWatch = client.observeAclLinkState(new CbtAclLinkObserver() {
            @Override
            public void onAclLinkChanged(@NonNull CbtDeviceSnapshot snapshot) {
                if (matches(snapshot.deviceId())) {
                    runtime.state(YoCbtLinkSession.this::evaluate);
                }
            }
        });
        profileWatch = client.observeProfileState(deviceId, new CbtProfileStateObserver() {
            @Override
            public void onProfileStateChanged(@NonNull CbtDeviceProfileState profileState) {
                if (matches(profileState.deviceId())) {
                    runtime.state(YoCbtLinkSession.this::evaluate);
                }
            }
        });

        // 2. 显式超时：到点必须收敛，不留悬挂。
        timeoutHandle = runtime.schedule(
                () -> runtime.state(this::failWithTimeout),
                config.readinessTimeoutMillis());

        // 3. 主动查询：覆盖「事件在订阅前已发生」的窗口。
        evaluate();
    }

    /**
     * 归并当前全部事实并推进状态机。
     *
     * <p>这是唯一的状态推进点：事件、查询结果、周期复核都汇到这里，
     * 保证判定逻辑只有一份。</p>
     */
    private void evaluate() {
        if (!active.get()) {
            return;
        }
        // 适配器关闭时链路不可能就绪：立即失败，不要让它退化成 UNKNOWN 后干等就绪超时。
        if (!client.isAdapterEnabled()) {
            terminate(new CbtFailure(CbtErrorCode.BLUETOOTH_DISABLED,
                    "蓝牙未开启: " + deviceId.value(), null, true, true, TAG, deviceId));
            return;
        }
        // 先做一次主动查询：把系统的「当前真实连接」写入快照仓库，
        // 否则同步 getter 只能读到旧缓存 / 空仓库，永远判 UNKNOWN。
        queryConnectedDevicesThenEvaluate();
    }

    /**
     * 主动查询系统已连接设备，落库后再判定。
     *
     * <p>为什么必须走 {@code queryConnectedDevices} 而不是直接读
     * {@code getAclState / getConnectedProfiles}：后两者只读「快照仓库」，
     * 而快照仓库只由广播与查询填充。冷启动或事件已错过时仓库为空，
     * 同步 getter 必然返回 UNKNOWN。{@code queryConnectedDevices} 会真正
     * 调 {@code BluetoothProfile.getConnectedDevices()} 并把结果写回仓库
     * （见 {@code YoCbtAndroidConnectedDeviceQuery#persistConnectedProfile}）。</p>
     */
    private void queryConnectedDevicesThenEvaluate() {
        if (querying) {
            return;
        }
        querying = true;
        CbtOperationHandle handle = client.queryConnectedDevices(
                new CbtConnectedDeviceObserver() {
                    @Override
                    public void onConnectedDevices(@NonNull List<CbtDeviceSnapshot> snapshots) {
                        boolean found = false;
                        for (CbtDeviceSnapshot snapshot : snapshots) {
                            if (matches(snapshot.deviceId())) {
                                found = true;
                                break;
                            }
                        }
                        final boolean present = found;
                        runtime.state(() -> {
                            querying = false;
                            advance(readReadiness());
                            if (!present && lastReported != CbtLinkReadiness.READY) {
                                // 系统查询没有该设备：说明确实未连接，可以安全发起连接。
                                requestConnectIfAllowed();
                            }
                        });
                    }

                    @Override
                    public void onConnectedDeviceQueryFailed(@NonNull CbtFailure failure) {
                        runtime.state(() -> {
                            querying = false;
                            // 查询失败不当作「未连接」：保持当前事实，交给复核与超时。
                            advance(readReadiness());
                        });
                    }
                });
        synchronized (lock) {
            queryHandle = handle;
        }
    }

    /** 依据事实推进：READY 收敛，DOWN 允许连接，UNKNOWN 仅等待。 */
    private void advance(@NonNull CbtLinkReadiness readiness) {
        report(readiness);
        if (readiness == CbtLinkReadiness.READY) {
            everReady = true;
            clearTimers();
            return;
        }
        if (readiness == CbtLinkReadiness.DOWN) {
            requestConnectIfAllowed();
        }
        scheduleRetry();
    }

    /** 明确未连接（或系统查询确认不在已连接集合）时才发起配对/音频连接。 */
    private void requestConnectIfAllowed() {
        if (!active.get() || bondingInProgress) {
            return;
        }
        BondFact bondFact = queryBondFact();
        if (bondFact == BondFact.BONDING) {
            bondingInProgress = true;
            ensureBondWatch();
            return;
        }
        if (bondFact == BondFact.NONE && config.createBondWhenUnbonded() && !bondRequested) {
            requestBond();
            return;
        }
        if (config.connectAudioWhenDown() && !audioRequested) {
            requestAudio();
        }
    }

    /** 读取三条平台事实并归并为就绪事实。查询失败按「未知」处理，不当作断开。 */
    @NonNull
    private CbtLinkReadiness readReadiness() {
        CbtAclLinkState aclState = null;
        Result<CbtAclLinkState> acl = client.getAclState(deviceId);
        if (acl.isSuccess() && acl.getValue() != null) {
            aclState = acl.getValue();
        }

        List<CbtProfileType> connected = null;
        Result<List<CbtProfileType>> profiles = client.getConnectedProfiles(deviceId);
        if (profiles.isSuccess()) {
            connected = profiles.getValue();
        }

        CbtProfileConnectionState a2dp = null;
        CbtProfileConnectionState headset = null;
        Result<CbtDeviceProfileSnapshot> snapshot = client.getDeviceProfileSnapshot(deviceId);
        if (snapshot.isSuccess() && snapshot.getValue() != null) {
            a2dp = stateOf(snapshot.getValue(), CbtProfileType.A2DP);
            headset = stateOf(snapshot.getValue(), CbtProfileType.HEADSET);
        }
        return CbtLinkReadiness.of(aclState, connected, a2dp, headset);
    }

    @Nullable
    private static CbtProfileConnectionState stateOf(@NonNull CbtDeviceProfileSnapshot snapshot,
                                                     @NonNull CbtProfileType type) {
        CbtDeviceProfileState state = snapshot.profileStates().get(type);
        return state == null ? null : state.state();
    }

    private void ensureBondWatch() {
        if (bondWatch != null) {
            return;
        }
        bondWatch = client.observeBondState(new CbtBondStateObserver() {
            @Override
            public void onBondStateChanged(@NonNull CbtDeviceSnapshot snapshot) {
                if (!matches(snapshot.deviceId())) {
                    return;
                }
                CbtBondState bondState = snapshot.bondState();
                runtime.state(() -> {
                    if (!active.get()) {
                        return;
                    }
                    if (bondState == CbtBondState.BONDING) {
                        bondingInProgress = true;
                    } else if (bondState == CbtBondState.BONDED) {
                        bondingInProgress = false;
                        evaluate();
                    } else if (bondState == CbtBondState.NONE
                            || bondState == CbtBondState.FAILED
                            || bondState == CbtBondState.REMOVED) {
                        bondingInProgress = false;
                        failWithBondRejected();
                    }
                });
            }
        });
    }

    private void requestBond() {
        bondRequested = true;
        bondingInProgress = true;
        ensureBondWatch();
        Result<Void> started = client.createBond(deviceId);
        if (started.isFailure()) {
            // 配对发起失败：清除配对中标记，退化为尝试音频连接。
            bondingInProgress = false;
            if (config.connectAudioWhenDown() && !audioRequested) {
                requestAudio();
            }
        }
    }

    private void requestAudio() {
        audioRequested = true;
        client.prepareProfileProxy(CbtProfileType.A2DP);
        client.prepareProfileProxy(CbtProfileType.HEADSET);
        Result<Void> started = client.connectAudio(deviceId);
        if (started.isFailure()) {
            // connectAudio 被拒不代表链路不可用：交给周期复核与超时收敛。
            scheduleRetry();
        }
    }

    /**
     * 首次就绪前的周期复核：覆盖「事件已错过」的窗口。
     *
     * <p><b>一旦到达过 READY 就不再复核</b>：此时就绪超时已被取消，若掉线后继续按
     * {@code queryRetryMillis} 无限复核，既不会收敛（{@code audioRequested} 已置位、不再重连），
     * 也会留下永不停止的定时任务（泄漏）。就绪后的链路保持/重连是上层策略，
     * 本会话只在事实变化时上报，不自行续命。</p>
     */
    private void scheduleRetry() {
        if (everReady || retryHandle != null || !active.get()) {
            return;
        }
        retryHandle = runtime.schedule(
                () -> runtime.state(() -> {
                    retryHandle = null;
                    evaluate();
                }),
                config.queryRetryMillis());
    }

    private void report(@NonNull CbtLinkReadiness readiness) {
        if (readiness == lastReported) {
            return;
        }
        lastReported = readiness;
        CbtLinkReadiness captured = readiness;
        runtime.callback(() -> {
            if (active.get()) {
                observer.onReadinessChanged(deviceId, captured);
            }
        });
    }

    /**
     * 终态失败：关闭会话、清定时器、取消全部订阅，只回调一次。
     *
     * <p>必须把 {@code active} 置 false 并取消 watcher：否则失败之后 ACL/Profile 事件仍会
     * 触发 {@code evaluate()}，可能重新排定时器（二次失败）甚至误报 READY——「终态」就不终态了。</p>
     */
    private void terminate(@NonNull CbtFailure failure) {
        if (!active.compareAndSet(true, false)) {
            return;
        }
        clearTimers();
        cancelWatches();
        runtime.callback(() -> observer.onLinkFailed(deviceId, failure));
    }

    private void failWithTimeout() {
        terminate(new CbtFailure(CbtErrorCode.LINK_READINESS_TIMEOUT,
                "链路就绪超时: " + deviceId.value(), null, true, true, TAG, deviceId));
    }

    private void failWithBondRejected() {
        terminate(new CbtFailure(CbtErrorCode.LINK_READINESS_REJECTED,
                "配对被拒绝: " + deviceId.value(), null, false, false, TAG, deviceId));
    }

    @NonNull
    private BondFact queryBondFact() {
        Result<List<CbtDeviceSnapshot>> bonded = client.getBondedDevices();
        if (bonded.isFailure() || bonded.getValue() == null) {
            // 查不到配对事实时按 UNKNOWN 处理，避免对未知态设备盲目发起配对。
            return BondFact.UNKNOWN;
        }
        for (CbtDeviceSnapshot snapshot : bonded.getValue()) {
            if (matches(snapshot.deviceId())) {
                return switch (snapshot.bondState()) {
                    case BONDED -> BondFact.BONDED;
                    case BONDING -> BondFact.BONDING;
                    case NONE, REMOVED, FAILED -> BondFact.NONE;
                    case UNKNOWN -> BondFact.UNKNOWN;
                };
            }
        }
        // 查询成功且目标不在系统已配对列表中：明确未配对。
        return BondFact.NONE;
    }

    private boolean matches(@Nullable CbtDeviceId other) {
        return other != null && deviceId.equals(other);
    }

    private void clearTimers() {
        if (timeoutHandle != null) {
            timeoutHandle.cancel();
            timeoutHandle = null;
        }
        if (retryHandle != null) {
            retryHandle.cancel();
            retryHandle = null;
        }
    }

    private void cancelWatches() {
        cancel(aclWatch);
        aclWatch = null;
        cancel(profileWatch);
        profileWatch = null;
        cancel(bondWatch);
        bondWatch = null;
        cancel(queryHandle);
        queryHandle = null;
    }

    private static void cancel(@Nullable CbtOperationHandle handle) {
        if (handle != null) {
            handle.cancel();
        }
    }

    @Override
    public void cancel() {
        if (!active.compareAndSet(true, false)) {
            return;
        }
        runtime.state(() -> {
            clearTimers();
            cancelWatches();
            runtime.callback(() -> observer.onLinkCancelled(deviceId));
        });
    }

    @Override
    public boolean isActive() {
        return active.get();
    }
}
