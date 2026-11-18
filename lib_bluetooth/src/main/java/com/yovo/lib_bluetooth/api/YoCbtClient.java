package com.yovo.lib_bluetooth.api;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.model.classic.CbtAclLinkObserver;
import com.yovo.lib_bluetooth.model.classic.CbtAclLinkState;
import com.yovo.lib_bluetooth.model.classic.CbtBondStateObserver;
import com.yovo.lib_bluetooth.model.classic.CbtCommand;
import com.yovo.lib_bluetooth.model.classic.CbtCommandObserver;
import com.yovo.lib_bluetooth.model.classic.CbtConnectedDeviceObserver;
import com.yovo.lib_bluetooth.model.classic.CbtDeviceId;
import com.yovo.lib_bluetooth.model.classic.CbtDiagnosticReport;
import com.yovo.lib_bluetooth.model.classic.CbtDeviceProfileSnapshot;
import com.yovo.lib_bluetooth.model.classic.CbtDeviceProfileState;
import com.yovo.lib_bluetooth.model.classic.CbtDeviceSnapshot;
import com.yovo.lib_bluetooth.model.classic.CbtDiscoveryConfig;
import com.yovo.lib_bluetooth.model.classic.CbtDiscoveryObserver;
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

import java.util.List;

/**
 * 经典蓝牙对外 API。客户端由 {@link com.yovo.lib_bluetooth.YoBluetoothSDK#classic} 创建。
 */
public interface YoCbtClient {

    @NonNull
    CbtOperationHandle discover(@NonNull CbtDiscoveryConfig config,
                                @NonNull CbtDiscoveryObserver observer);

    @NonNull
    Result<Void> stopDiscovery();

    @NonNull
    Result<List<CbtDeviceSnapshot>> getBondedDevices();

    @NonNull
    Result<Void> createBond(@NonNull CbtDeviceId deviceId);

    @NonNull
    Result<Void> cancelBondProcess(@NonNull CbtDeviceId deviceId);

    @NonNull
    Result<Void> removeBond(@NonNull CbtDeviceId deviceId);

    @NonNull
    CbtOperationHandle observeBondState(@NonNull CbtBondStateObserver observer);

    @NonNull
    CbtOperationHandle observeAclLinkState(@NonNull CbtAclLinkObserver observer);

    @NonNull
    Result<CbtAclLinkState> getAclState(@NonNull CbtDeviceId deviceId);

    @NonNull
    Result<List<CbtDeviceId>> getAclConnectedDevices();

    @NonNull
    CbtOperationHandle queryConnectedDevices(@NonNull CbtConnectedDeviceObserver observer);

    @NonNull
    CbtOperationHandle discoverServices(@NonNull CbtDeviceId deviceId,
                                        @NonNull CbtServiceDiscoveryConfig config,
                                        @NonNull CbtServiceDiscoveryObserver observer);

    @NonNull
    Result<Void> prepareProfileProxy(@NonNull CbtProfileType profileType);

    /**
     * 请求把已配对设备的 A2DP 或 Headset 连上。
     * 接受请求不等于已经连上，调用方继续看 Profile 状态。
     */
    @NonNull
    Result<Void> connectAudio(@NonNull CbtDeviceId deviceId);

    @NonNull
    Result<CbtProfileAggregateState> getProfileAggregateState(@NonNull CbtProfileType profileType);

    @NonNull
    Result<CbtDeviceProfileState> getDeviceProfileState(@NonNull CbtDeviceId deviceId,
                                                        @NonNull CbtProfileType profileType);

    @NonNull
    Result<CbtDeviceProfileSnapshot> getDeviceProfileSnapshot(@NonNull CbtDeviceId deviceId);

    @NonNull
    Result<List<CbtProfileType>> getConnectedProfiles(@NonNull CbtDeviceId deviceId);

    @NonNull
    CbtOperationHandle observeProfileState(@NonNull CbtDeviceId deviceId,
                                           @NonNull CbtProfileStateObserver observer);

    @NonNull
    CbtOperationHandle observeProfileState(@NonNull CbtProfileStateObserver observer);

    @NonNull
    CbtOperationHandle connectSpp(@NonNull CbtDeviceId deviceId,
                                  @NonNull CbtSppEndpoint endpoint,
                                  @NonNull CbtSppConfig config,
                                  @NonNull CbtSppConnectionObserver observer);

    /**
     * 打开「链路就绪」会话：库内完成 配对 → ACL → 音频 Profile → 就绪 的完整编排。
     *
     * <p>这是经典链路的<b>唯一</b>就绪入口。上层不得自行组合
     * {@code getAclState / getConnectedProfiles / connectAudio / observe*} 来判定链路，
     * 因为那会重复实现同一套三态判定，并极易把「未知」误判成「未连接」——
     * 对已连接的设备重复请求 CONNECT 会被系统忽略，不产生任何状态广播，最终只能等到超时。</p>
     *
     * <p>实现语义（对齐 Nordic BleManager 的「查询 + 事件 + 显式超时」模型）：</p>
     * <ul>
     *   <li>进入会话立即主动查询一次既有链路，命中即 {@link CbtLinkReadiness#READY}，
     *       不发起任何配对/连接请求；</li>
     *   <li>只有明确 {@link CbtLinkReadiness#DOWN} 才按配置发起配对或音频连接；</li>
     *   <li>{@link CbtLinkReadiness#UNKNOWN} 只继续等待并周期复核，绝不据此重连；</li>
     *   <li>到 {@link CbtLinkSessionConfig#readinessTimeoutMillis()} 仍未就绪即回调失败。</li>
     * </ul>
     *
     * @param deviceId 目标设备
     * @param config   会话参数（超时、复核间隔、是否自动配对/连音频）
     * @param observer 就绪事实与失败回调
     * @return 会话句柄；{@link CbtLinkSessionHandle#cancel()} 幂等且取消后不再回调
     */
    @NonNull
    CbtLinkSessionHandle openLinkSession(@NonNull CbtDeviceId deviceId,
                                         @NonNull CbtLinkSessionConfig config,
                                         @NonNull CbtLinkSessionObserver observer);

    @NonNull
    Result<Void> disconnectSpp(@NonNull CbtSppConnectionId connectionId);

    @NonNull
    Result<Void> disconnectSppByDevice(@NonNull CbtDeviceId deviceId);

    @NonNull
    Result<Void> disconnectAllSpp();

    @NonNull
    Result<Void> send(@NonNull CbtSppConnectionId connectionId, @NonNull byte[] data);

    @NonNull
    Result<Void> sendCommand(@NonNull CbtSppConnectionId connectionId,
                             @NonNull CbtCommand command);

    @NonNull
    CbtOperationHandle observeCommandResult(@NonNull CbtSppConnectionId connectionId,
                                            @NonNull CbtCommandObserver observer);

    @NonNull
    CbtOperationHandle observeSppState(@NonNull CbtSppConnectionId connectionId,
                                       @NonNull CbtSppStateObserver observer);

    @NonNull
    Result<List<CbtSppConnectionSnapshot>> getActiveSppConnections();

    @NonNull
    Result<List<CbtSppConnectionSnapshot>> getSppConnections(@NonNull CbtDeviceId deviceId);

    @NonNull
    Result<List<CbtDeviceSnapshot>> getDeviceSnapshots();

    boolean isAdapterEnabled();

    @NonNull
    Result<CbtDiagnosticReport> exportDiagnosticReport();

    void release();
}
