package com.yovo.biz_ggec_wy.provider.lib_bluetooth;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_ggec_wy.api.WyControl;
import com.yovo.biz_ggec_wy.internal.center.port.WyClassicRadioPort;
import com.yovo.biz_ggec_wy.provider.lib_common.log.WyLog;
import com.yovo.lib_bluetooth.YoBluetoothSDK;
import com.yovo.lib_bluetooth.api.YoCbtClient;
import com.yovo.lib_bluetooth.model.classic.CbtDeviceId;
import com.yovo.lib_bluetooth.model.classic.CbtDeviceSnapshot;
import com.yovo.lib_bluetooth.model.classic.CbtDiscoveryConfig;
import com.yovo.lib_bluetooth.model.classic.CbtDiscoveryObserver;
import com.yovo.lib_bluetooth.model.classic.CbtDiscoveryResult;
import com.yovo.lib_bluetooth.model.classic.CbtDiscoveryState;
import com.yovo.lib_bluetooth.model.classic.CbtFailure;
import com.yovo.lib_bluetooth.model.classic.CbtLinkReadiness;
import com.yovo.lib_bluetooth.model.classic.CbtLinkSessionConfig;
import com.yovo.lib_bluetooth.model.classic.CbtLinkSessionHandle;
import com.yovo.lib_bluetooth.model.classic.CbtLinkSessionObserver;
import com.yovo.lib_bluetooth.model.classic.CbtOperationHandle;
import com.yovo.lib_bluetooth.model.classic.CbtSppConfig;
import com.yovo.lib_bluetooth.model.classic.CbtSppConnectionId;
import com.yovo.lib_bluetooth.model.classic.CbtSppConnectionObserver;
import com.yovo.lib_bluetooth.model.classic.CbtSppConnectionSnapshot;
import com.yovo.lib_bluetooth.model.classic.CbtSppConnectionState;
import com.yovo.lib_bluetooth.model.classic.CbtSppEndpoint;
import com.yovo.lib_bluetooth.model.common.Failure;
import com.yovo.lib_bluetooth.model.common.Result;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * WY 经典蓝牙适配：<b>只做协议适配与 SPP 通道编排</b>，链路就绪由 lib_bluetooth 负责。
 *
 * <h3>职责边界</h3>
 * <table>
 *   <tr><th>归 lib_bluetooth</th><th>归本类</th></tr>
 *   <tr>
 *     <td>配对 / ACL / 音频 Profile 的就绪判定与编排、超时收敛、未知态等待</td>
 *     <td>WY 专有的两条 SPP 通道（控制流 7777…、音频流 8888…）、
 *         字节收发、状态到 {@link WyControl.Failure} 的映射</td>
 *   </tr>
 * </table>
 *
 * <p>此前本类自行组合 {@code getAclState / getConnectedProfiles / connectAudio / observe*}
 * 判定链路，把「未知」当成「未连接」，对已连接设备重复请求 CONNECT 被系统忽略，
 * 导致必然超时。该判定已上移到 {@link YoCbtClient#openLinkSession}，本类不再持有任何
 * ACL/Profile 状态机。</p>
 */
public final class WyClassicRadio implements WyClassicRadioPort {

    private static final String TAG = "WyClassicRadio";

    public static final UUID CONTROL_UUID = UUID.fromString("77777777-7777-7777-7777-777777777777");
    public static final UUID AUDIO_UUID = UUID.fromString("88888888-8888-8888-8888-888888888888");

    /** 控制流端点 UUID：WY 协议专有，供测试与诊断引用同一取值。 */
    @NonNull
    public static UUID controlUuid() {
        return CONTROL_UUID;
    }

    /** 音频流端点 UUID：WY 协议专有，供测试与诊断引用同一取值。 */
    @NonNull
    public static UUID audioUuid() {
        return AUDIO_UUID;
    }

    /** SPP 通道连接超时：与链路就绪超时解耦，通道本身只需较短窗口。 */
    private static final long SPP_CONNECT_TIMEOUT_MILLIS = 15_000L;

    private final YoCbtClient client;
    private final Object lock = new Object();

    @Nullable
    private CbtOperationHandle discoveryHandle;

    @Nullable
    private CbtLinkSessionHandle linkSession;
    @Nullable
    private CbtOperationHandle controlHandle;
    @Nullable
    private CbtOperationHandle audioHandle;

    @Nullable
    private CbtSppConnectionId controlConnectionId;
    @Nullable
    private CbtSppConnectionId audioConnectionId;

    private boolean controlConnected;
    private boolean audioConnected;

    /** 音频连接代次：每次新建音频通道递增，用于丢弃旧音频连接的迟到终态回调。 */
    private int audioGeneration;

    @Nullable
    private CbtDeviceId currentDeviceId;
    @Nullable
    private SppStateListener currentListener;
    /** 会话代次：每次 connect/disconnect 递增，用于丢弃过期回调。 */
    private int generation;

    /**
     * 是否处于「本地主动关闭音频通道」窗口：由 {@link #disconnectAudioSpp()} 置位、
     * {@link #connectAudioSpp} 起始与新连接建立后复位。用于把主动收尾产生的
     * DISCONNECTED/FAILED 从对外 failure 中抹掉，避免正常停止被误报成 DEVICE_ERROR。
     */
    private boolean audioCloseIntended;

    public WyClassicRadio(@NonNull YoCbtClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    @NonNull
    public static WyClassicRadio open(@NonNull Context context) {
        Context app = context.getApplicationContext();
        Context target = app != null ? app : context;
        return new WyClassicRadio(YoBluetoothSDK.classic(target));
    }

    // ── 已配对设备 ────────────────────────────────────────────────

    @NonNull
    @Override
    public List<BondedDevice> getBondedDevices() {
        Result<List<CbtDeviceSnapshot>> bonded = client.getBondedDevices();
        if (bonded.isFailure() || bonded.getValue() == null) {
            return Collections.emptyList();
        }
        List<BondedDevice> result = new ArrayList<>();
        for (CbtDeviceSnapshot snapshot : bonded.getValue()) {
            if (snapshot.deviceId() == null) {
                continue;
            }
            String address = snapshot.identity().classicAddress();
            String name = snapshot.identity().classicName();
            result.add(new BondedDevice(name, address, false));
        }
        return Collections.unmodifiableList(result);
    }

    // ── 发现 ──────────────────────────────────────────────────────

    @Override
    public void startDiscovery(@NonNull DiscoveryListener listener) {
        Objects.requireNonNull(listener, "listener");
        stopDiscovery();
        CbtDiscoveryConfig config = CbtDiscoveryConfig.builder()
                .timeoutMillis(12_000L)
                .requireName(false)
                .build();
        CbtOperationHandle handle = client.discover(config, new CbtDiscoveryObserver() {
            @Override
            public void onDiscoveryStateChanged(@NonNull CbtDiscoveryState state) {
                if (state == CbtDiscoveryState.FINISHED) {
                    listener.onDiscoveryFinished();
                }
            }

            @Override
            public void onDeviceFound(@NonNull CbtDiscoveryResult result) {
                CbtDeviceSnapshot snapshot = result.snapshot();
                String name = snapshot.identity().classicName();
                listener.onDeviceDiscovered(
                        name != null ? name : "",
                        snapshot.identity().classicAddress(),
                        snapshot.rssi(),
                        result.timestampMillis());
            }

            @Override
            public void onDiscoveryFailed(@NonNull CbtFailure failure) {
                listener.onDiscoveryFailed(mapFailure(failure));
            }
        });
        synchronized (lock) {
            discoveryHandle = handle;
        }
    }

    @Override
    public void stopDiscovery() {
        CbtOperationHandle handle;
        synchronized (lock) {
            handle = discoveryHandle;
            discoveryHandle = null;
        }
        if (handle != null) {
            handle.cancel();
        }
        client.stopDiscovery();
    }

    // ── 连接：链路就绪交给 lib，本类只编排 SPP ──────────────────────

    @Override
    public void connect(@NonNull String classicAddress, @NonNull SppStateListener listener) {
        Objects.requireNonNull(classicAddress, "classicAddress");
        Objects.requireNonNull(listener, "listener");

        final int current;
        synchronized (lock) {
            cancelLinkSessionLocked();
            current = ++generation;
            currentDeviceId = CbtDeviceId.fromAddress(classicAddress);
            currentListener = listener;
            controlConnected = false;
            audioConnected = false;
        }

        listener.onControlStateChanged(SppState.CONNECTING, null);

        CbtDeviceId deviceId = CbtDeviceId.fromAddress(classicAddress);
        CbtLinkSessionHandle session = client.openLinkSession(
                deviceId,
                CbtLinkSessionConfig.defaults(),
                new CbtLinkSessionObserver() {
                    @Override
                    public void onReadinessChanged(@NonNull CbtDeviceId id,
                                                   @NonNull CbtLinkReadiness readiness) {
                        WyLog.d(TAG, "链路就绪事实: " + readiness + ", " + id.value());
                        if (readiness == CbtLinkReadiness.READY) {
                            synchronized (lock) {
                                if (current != generation) {
                                    return;
                                }
                            }
                            connectControlChannel(current, deviceId);
                        }
                    }

                    @Override
                    public void onLinkFailed(@NonNull CbtDeviceId id, @NonNull CbtFailure failure) {
                        WyLog.w(TAG, "链路就绪失败: " + failure.message());
                        synchronized (lock) {
                            if (current != generation) {
                                return;
                            }
                        }
                        listener.onControlStateChanged(SppState.FAILED, mapFailure(failure));
                    }

                    @Override
                    public void onLinkCancelled(@NonNull CbtDeviceId id) {
                        // 主动取消不产生失败回调。
                    }
                });

        synchronized (lock) {
            if (current != generation) {
                session.cancel();
                return;
            }
            linkSession = session;
        }
    }

    /** 建立控制流 SPP：音频 SPP 按需在录音时另建（见 {@link #connectAudioSpp}）。 */
    private void connectControlChannel(int gen, @NonNull CbtDeviceId deviceId) {
        CbtSppConfig config = CbtSppConfig.builder()
                .connectTimeoutMillis(SPP_CONNECT_TIMEOUT_MILLIS)
                .build();
        SppStateListener listener;
        synchronized (lock) {
            if (gen != generation) {
                return;
            }
            listener = currentListener;
            if (controlHandle != null) {
                controlHandle.cancel();
                controlHandle = null;
            }
        }
        if (listener == null) {
            return;
        }

        CbtOperationHandle handle = client.connectSpp(
                deviceId,
                CbtSppEndpoint.uuid(CONTROL_UUID),
                config,
                new CbtSppConnectionObserver() {
                    @Override
                    public void onSppStateChanged(@NonNull CbtSppConnectionSnapshot snapshot) {
                        synchronized (lock) {
                            if (gen != generation) {
                                return;
                            }
                            applyChannelState(snapshot, true);
                        }
                        listener.onControlStateChanged(
                                mapSppState(snapshot.state()),
                                snapshot.failure() != null ? mapFailure(snapshot.failure()) : null);
                    }

                    @Override
                    public void onSppDataReceived(@NonNull CbtSppConnectionId connectionId,
                                                  @NonNull byte[] data) {
                        listener.onControlDataReceived(data);
                    }
                });

        synchronized (lock) {
            if (gen != generation) {
                handle.cancel();
                return;
            }
            controlHandle = handle;
        }
    }

    @Override
    public void connectAudioSpp(@NonNull ActionCallback callback) {
        Objects.requireNonNull(callback, "callback");
        final int gen;
        final int myAudioGen;
        final CbtDeviceId deviceId;
        final SppStateListener listener;
        synchronized (lock) {
            // 每一次新的音频连接尝试都清掉上一轮的「主动关闭」标记。
            audioCloseIntended = false;
            if (audioConnected) {
                callback.onResult(true, null);
                return;
            }
            gen = generation;
            myAudioGen = ++audioGeneration;
            deviceId = currentDeviceId;
            listener = currentListener;
            if (deviceId == null || listener == null || !controlConnected) {
                callback.onResult(false, WyControl.Failure.DISCONNECTED);
                return;
            }
            if (audioHandle != null) {
                audioHandle.cancel();
                audioHandle = null;
            }
        }

        CbtSppConfig config = CbtSppConfig.builder()
                .connectTimeoutMillis(SPP_CONNECT_TIMEOUT_MILLIS)
                .build();
        CbtOperationHandle handle = client.connectSpp(
                deviceId,
                CbtSppEndpoint.uuid(AUDIO_UUID),
                config,
                new CbtSppConnectionObserver() {
                    private boolean callbackFired;

                    @Override
                    public void onSppStateChanged(@NonNull CbtSppConnectionSnapshot snapshot) {
                        SppState state;
                        WyControl.Failure failure;
                        boolean intendedClose;
                        synchronized (lock) {
                            if (gen != generation || myAudioGen != audioGeneration) {
                                // 链路代次或音频连接代次已过期：丢弃旧音频连接的迟到终态。
                                return;
                            }
                            applyChannelState(snapshot, false);
                            state = mapSppState(snapshot.state());
                            failure = snapshot.failure() != null
                                    ? mapFailure(snapshot.failure()) : null;
                            if (state == SppState.CONNECTED) {
                                // 新连接已建立：复位主动关闭标记。
                                audioCloseIntended = false;
                            }
                            intendedClose = audioCloseIntended;
                        }
                        // 本地主动关闭音频通道期间，终态不对外报 failure（正常收尾不是设备错误）。
                        WyControl.Failure reportedFailure =
                                intendedClose
                                        && (state == SppState.DISCONNECTED || state == SppState.FAILED)
                                        ? null : failure;
                        listener.onAudioStateChanged(state, reportedFailure);
                        synchronized (this) {
                            if (callbackFired) {
                                return;
                            }
                            if (state == SppState.CONNECTED) {
                                callbackFired = true;
                                callback.onResult(true, null);
                            } else if (state == SppState.FAILED || state == SppState.DISCONNECTED) {
                                callbackFired = true;
                                callback.onResult(false, failure != null
                                        ? failure : WyControl.Failure.DEVICE_ERROR);
                            }
                        }
                    }

                    @Override
                    public void onSppDataReceived(@NonNull CbtSppConnectionId connectionId,
                                                  @NonNull byte[] data) {
                        listener.onAudioDataReceived(data);
                    }
                });

        synchronized (lock) {
            if (gen != generation) {
                handle.cancel();
                return;
            }
            audioHandle = handle;
        }
    }

    /** 把 SPP 快照落到通道状态字段（调用方须持锁）。 */
    private void applyChannelState(@NonNull CbtSppConnectionSnapshot snapshot, boolean control) {
        CbtSppConnectionState state = snapshot.state();
        if (state == CbtSppConnectionState.CONNECTED) {
            if (control) {
                controlConnectionId = snapshot.connectionId();
                controlConnected = true;
            } else {
                audioConnectionId = snapshot.connectionId();
                audioConnected = true;
            }
        } else if (state == CbtSppConnectionState.DISCONNECTED
                || state == CbtSppConnectionState.FAILED) {
            if (control) {
                controlConnectionId = null;
                controlConnected = false;
            } else {
                audioConnectionId = null;
                audioConnected = false;
            }
        }
    }

    @Override
    public void disconnectAudioSpp() {
        synchronized (lock) {
            // 标记为主动关闭：迟到的 DISCONNECTED/FAILED 不对外报 DEVICE_ERROR。
            audioCloseIntended = true;
            if (audioHandle != null) {
                audioHandle.cancel();
                audioHandle = null;
            }
            if (audioConnectionId != null) {
                client.disconnectSpp(audioConnectionId);
                audioConnectionId = null;
            }
            audioConnected = false;
        }
    }

    @Override
    public void disconnect() {
        CbtSppConnectionId control;
        CbtSppConnectionId audio;
        synchronized (lock) {
            generation++;
            cancelLinkSessionLocked();
            currentDeviceId = null;
            currentListener = null;
            if (controlHandle != null) {
                controlHandle.cancel();
                controlHandle = null;
            }
            if (audioHandle != null) {
                audioHandle.cancel();
                audioHandle = null;
            }
            control = controlConnectionId;
            audio = audioConnectionId;
            controlConnectionId = null;
            audioConnectionId = null;
            controlConnected = false;
            audioConnected = false;
        }
        if (control != null) {
            client.disconnectSpp(control);
        }
        if (audio != null) {
            client.disconnectSpp(audio);
        }
    }

    // ── 收发 ──────────────────────────────────────────────────────

    @Override
    public void sendControl(@NonNull byte[] data, @NonNull SendCallback callback) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(callback, "callback");
        CbtSppConnectionId connectionId;
        synchronized (lock) {
            connectionId = controlConnectionId;
        }
        if (connectionId == null) {
            callback.onComplete(false, WyControl.Failure.DISCONNECTED);
            return;
        }
        Result<Void> sent = client.send(connectionId, data);
        if (sent.isFailure()) {
            callback.onComplete(false, mapFailure(sent.getFailure()));
            return;
        }
        callback.onComplete(true, null);
    }
    @Override
    public boolean isControlConnected() {
        synchronized (lock) {
            return controlConnected;
        }
    }

    @Override
    public boolean isAudioConnected() {
        synchronized (lock) {
            return audioConnected;
        }
    }

    @Override
    public boolean isClassicConnected(@NonNull String classicAddress) {
        Objects.requireNonNull(classicAddress, "classicAddress");
        // 链路事实只在 lib 内判定：这里只回答「本会话的控制通道是否已就绪」，
        // 不再自行组合 ACL/Profile 查询，避免出现第二套三态判定。
        synchronized (lock) {
            return controlConnected;
        }
    }

    @Override
    public void release() {
        disconnect();
        stopDiscovery();
        client.release();
    }

    private void cancelLinkSessionLocked() {
        if (linkSession != null) {
            linkSession.cancel();
            linkSession = null;
        }
    }

    // ── 状态映射 ──────────────────────────────────────────────────

    @NonNull
    private static SppState mapSppState(@Nullable CbtSppConnectionState state) {
        if (state == null) {
            return SppState.IDLE;
        }
        return switch (state) {
            case IDLE -> SppState.IDLE;
            case CONNECTING -> SppState.CONNECTING;
            case CONNECTED -> SppState.CONNECTED;
            case DISCONNECTING, DISCONNECTED -> SppState.DISCONNECTED;
            case FAILED -> SppState.FAILED;
        };
    }

    /**
     * 失败映射单点：{@code lib_bluetooth} 的失败类型 → WY 对外失败枚举。
     *
     * <p>接受基类 {@link Failure} 并在此做唯一一次类型收窄，
     * 调用方不再各自判断具体子类。</p>
     */
    @NonNull
    private static WyControl.Failure mapFailure(@Nullable Failure failure) {
        if (!(failure instanceof CbtFailure cbtFailure)) {
            return WyControl.Failure.INTERNAL_ERROR;
        }
        return switch (cbtFailure.errorCode()) {
            case BLUETOOTH_DISABLED -> WyControl.Failure.BLUETOOTH_DISABLED;
            case PERMISSION_DENIED -> WyControl.Failure.PERMISSION_DENIED;
            case SPP_CONNECT_TIMEOUT, LINK_READINESS_TIMEOUT -> WyControl.Failure.TIMEOUT;
            case LINK_READINESS_REJECTED, BOND_FAILED -> WyControl.Failure.REJECTED;
            // 通道被本地/远端关闭都属于「断开连接」，不是设备异常。
            case SPP_LOCAL_CLOSED, SPP_REMOTE_CLOSED -> WyControl.Failure.DISCONNECTED;
            default -> WyControl.Failure.DEVICE_ERROR;
        };
    }
}
