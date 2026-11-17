package com.yovo.biz_ggec_wy.provider.lib_bluetooth;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_ggec_wy.api.WyControl;
import com.yovo.biz_ggec_wy.internal.center.port.WyClassicRadioPort;
import com.yovo.biz_ggec_wy.provider.lib_common.log.WyLog;
import com.yovo.lib_bluetooth.YoBluetoothSDK;
import com.yovo.lib_bluetooth.api.YoCbtClient;
import com.yovo.lib_bluetooth.model.classic.CbtAclLinkObserver;
import com.yovo.lib_bluetooth.model.classic.CbtAclLinkState;
import com.yovo.lib_bluetooth.model.classic.CbtBondState;
import com.yovo.lib_bluetooth.model.classic.CbtBondStateObserver;
import com.yovo.lib_bluetooth.model.classic.CbtDeviceId;
import com.yovo.lib_bluetooth.model.classic.CbtDeviceProfileState;
import com.yovo.lib_bluetooth.model.classic.CbtDeviceSnapshot;
import com.yovo.lib_bluetooth.model.classic.CbtDiscoveryConfig;
import com.yovo.lib_bluetooth.model.classic.CbtDiscoveryObserver;
import com.yovo.lib_bluetooth.model.classic.CbtDiscoveryResult;
import com.yovo.lib_bluetooth.model.classic.CbtDiscoveryState;
import com.yovo.lib_bluetooth.model.classic.CbtErrorCode;
import com.yovo.lib_bluetooth.model.classic.CbtFailure;
import com.yovo.lib_bluetooth.model.classic.CbtOperationHandle;
import com.yovo.lib_bluetooth.model.classic.CbtProfileConnectionState;
import com.yovo.lib_bluetooth.model.classic.CbtProfileStateObserver;
import com.yovo.lib_bluetooth.model.classic.CbtProfileType;
import com.yovo.lib_bluetooth.model.classic.CbtSppConfig;
import com.yovo.lib_bluetooth.model.classic.CbtSppConnectionId;
import com.yovo.lib_bluetooth.model.classic.CbtSppConnectionObserver;
import com.yovo.lib_bluetooth.model.classic.CbtSppConnectionSnapshot;
import com.yovo.lib_bluetooth.model.classic.CbtSppConnectionState;
import com.yovo.lib_bluetooth.model.classic.CbtSppEndpoint;
import com.yovo.lib_bluetooth.model.common.Result;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 经典蓝牙栈适配器：实现 {@link WyClassicRadioPort}，封装 {@link YoCbtClient}。
 *
 * <p>连接策略：
 * 1. 发起连接时，先检查经典蓝牙链路（ACL / Profile）是否已就绪；
 * 2. 若未就绪，先调用 lib_bluetooth 执行配对与连接（createBond / connectAudio）；
 * 3. 经典蓝牙物理链路就绪后，再正式建立控制流与音频流双通道 SPP；
 * 4. 严禁在经典蓝牙未连时直接拒绝连接。</p>
 */
public final class WyClassicRadio implements WyClassicRadioPort {

    private static final String TAG = "WyClassicRadio";

    public static final UUID CONTROL_UUID = UUID.fromString("77777777-7777-7777-7777-777777777777");
    public static final UUID AUDIO_UUID = UUID.fromString("88888888-8888-8888-8888-888888888888");

    public interface Delay {
        void postDelayed(@NonNull Runnable task, long delayMillis);

        void removeCallbacks(@NonNull Runnable task);
    }

    private final YoCbtClient client;
    private final Delay delay;
    private final Object lock = new Object();

    @Nullable
    private CbtOperationHandle discoveryHandle;
    @Nullable
    private CbtOperationHandle controlHandle;
    @Nullable
    private CbtOperationHandle audioHandle;
    @Nullable
    private CbtSppConnectionId controlConnectionId;
    @Nullable
    private CbtSppConnectionId audioConnectionId;

    private int connectGeneration;
    private boolean classicConnectSettled;
    @Nullable
    private Runnable classicTimeoutRunnable;
    @Nullable
    private CbtOperationHandle bondHandle;
    @Nullable
    private CbtOperationHandle aclHandle;
    @Nullable
    private CbtOperationHandle profileHandle;

    private boolean controlConnected;
    private boolean audioConnected;

    public WyClassicRadio(@NonNull YoCbtClient client) {
        this(client, createDefaultDelay());
    }

    public WyClassicRadio(@NonNull YoCbtClient client, @NonNull Delay delay) {
        this.client = Objects.requireNonNull(client, "client");
        this.delay = Objects.requireNonNull(delay, "delay");
    }

    private static Delay createDefaultDelay() {
        if (Looper.getMainLooper() != null) {
            Handler handler = new Handler(Looper.getMainLooper());
            return new Delay() {
                @Override
                public void postDelayed(@NonNull Runnable task, long delayMillis) {
                    handler.postDelayed(task, delayMillis);
                }

                @Override
                public void removeCallbacks(@NonNull Runnable task) {
                    handler.removeCallbacks(task);
                }
            };
        }
        return new Delay() {
            @Override
            public void postDelayed(@NonNull Runnable task, long delayMillis) {
            }

            @Override
            public void removeCallbacks(@NonNull Runnable task) {
            }
        };
    }

    @NonNull
    public static WyClassicRadio open(@NonNull Context context) {
        Context app = context.getApplicationContext();
        Context target = app != null ? app : context;
        return new WyClassicRadio(YoBluetoothSDK.classic(target));
    }

    @Override
    public void startDiscovery(@NonNull DiscoveryListener listener) {
        Objects.requireNonNull(listener, "listener");
        stopDiscovery();
        synchronized (lock) {
            CbtDiscoveryConfig config = CbtDiscoveryConfig.builder()
                    .timeoutMillis(12000L)
                    .requireName(false)
                    .build();
            discoveryHandle = client.discover(config, new CbtDiscoveryObserver() {
                @Override
                public void onDiscoveryStateChanged(@NonNull CbtDiscoveryState state) {
                    if (state == CbtDiscoveryState.FINISHED) {
                        listener.onDiscoveryFinished();
                    }
                }

                @Override
                public void onDeviceFound(@NonNull CbtDiscoveryResult result) {
                    String name = result.snapshot().identity().classicName();
                    String address = result.snapshot().identity().classicAddress();
                    int rssi = result.snapshot().rssi();
                    long ts = result.timestampMillis();
                    listener.onDeviceDiscovered(name, address, rssi, ts);
                }

                @Override
                public void onDiscoveryFailed(@NonNull CbtFailure failure) {
                    listener.onDiscoveryFailed(mapFailure(failure));
                }
            });
        }
    }

    @Override
    public void stopDiscovery() {
        synchronized (lock) {
            if (discoveryHandle != null) {
                discoveryHandle.cancel();
                discoveryHandle = null;
            }
            client.stopDiscovery();
        }
    }

    @Override
    public void connect(@NonNull String classicAddress, @NonNull SppStateListener listener) {
        Objects.requireNonNull(classicAddress, "classicAddress");
        Objects.requireNonNull(listener, "listener");
        disconnect();

        final int currentGen;
        synchronized (lock) {
            currentGen = ++connectGeneration;
            classicConnectSettled = false;
        }

        final CbtDeviceId deviceId = CbtDeviceId.fromAddress(classicAddress);

        // 1. 如果经典蓝牙已经连通（ACL 或 Profile 已就绪），直接连接 SPP 双通道
        if (isClassicConnected(classicAddress)) {
            WyLog.i(TAG, "经典蓝牙已就绪，直接发起 SPP 双通道连接: " + classicAddress);
            connectSppChannels(currentGen, deviceId, listener);
            return;
        }

        // 2. 经典蓝牙未连通，先通过 lib_bluetooth 建立经典蓝牙连接
        WyLog.i(TAG, "经典蓝牙未连接，先调用 lib_bluetooth 建立经典连接: " + classicAddress);
        listener.onControlStateChanged(SppState.CONNECTING, null);

        client.prepareProfileProxy(CbtProfileType.A2DP);
        client.prepareProfileProxy(CbtProfileType.HEADSET);

        synchronized (lock) {
            classicTimeoutRunnable = () -> handleClassicTimeout(currentGen, listener);
            delay.postDelayed(classicTimeoutRunnable, 15000L);

            // 监听 ACL 链路状态（ACL 连接代表经典蓝牙物理链路已连通）
            aclHandle = client.observeAclLinkState(new CbtAclLinkObserver() {
                @Override
                public void onAclLinkChanged(@NonNull CbtDeviceSnapshot snapshot) {
                    if (snapshot.deviceId() != null && deviceId.equals(snapshot.deviceId())) {
                        if (snapshot.aclLinkState() == CbtAclLinkState.CONNECTED) {
                            WyLog.i(TAG, "监听到 ACL 链路已连通: " + classicAddress);
                            onClassicLinkEstablished(currentGen, deviceId, listener);
                        }
                    }
                }
            });

            // 监听 Profile 连接状态（A2DP 或 HEADSET 连上也可代表经典蓝牙已就绪）
            profileHandle = client.observeProfileState(deviceId, new CbtProfileStateObserver() {
                @Override
                public void onProfileStateChanged(@NonNull CbtDeviceProfileState profileState) {
                    if (profileState.state() == CbtProfileConnectionState.CONNECTED) {
                        WyLog.i(TAG, "监听到 Profile 已连通: " + profileState.profileType() + ", " + classicAddress);
                        onClassicLinkEstablished(currentGen, deviceId, listener);
                    }
                }
            });

            boolean bonded = isBonded(deviceId);
            if (!bonded) {
                WyLog.i(TAG, "设备未配对，先发起配对: " + classicAddress);
                bondHandle = client.observeBondState(new CbtBondStateObserver() {
                    @Override
                    public void onBondStateChanged(@NonNull CbtDeviceSnapshot snapshot) {
                        if (snapshot.deviceId() != null && deviceId.equals(snapshot.deviceId())) {
                            if (snapshot.bondState() == CbtBondState.BONDED) {
                                WyLog.i(TAG, "配对成功，发起 connectAudio 建立经典蓝牙连接: " + classicAddress);
                                Result<Void> audioRes = client.connectAudio(deviceId);
                                if (audioRes.isFailure()) {
                                    WyLog.w(TAG, "connectAudio 发起失败，直接尝试建立 SPP: " + audioRes.getFailure());
                                    onClassicLinkEstablished(currentGen, deviceId, listener);
                                }
                            } else if (snapshot.bondState() == CbtBondState.NONE) {
                                WyLog.w(TAG, "配对失败或取消: " + classicAddress);
                                handleClassicFailed(currentGen, listener, WyControl.Failure.REJECTED);
                            }
                        }
                    }
                });
                Result<Void> bondRes = client.createBond(deviceId);
                if (bondRes.isFailure()) {
                    WyLog.w(TAG, "createBond 发起失败，尝试直接 connectAudio: " + bondRes.getFailure());
                    Result<Void> audioRes = client.connectAudio(deviceId);
                    if (audioRes.isFailure()) {
                        WyLog.w(TAG, "connectAudio 也失败，直接尝试建立 SPP: " + audioRes.getFailure());
                        onClassicLinkEstablished(currentGen, deviceId, listener);
                    }
                }
            } else {
                WyLog.i(TAG, "设备已配对，发起 connectAudio 建立经典蓝牙音频/ACL: " + classicAddress);
                Result<Void> audioRes = client.connectAudio(deviceId);
                if (audioRes.isFailure()) {
                    WyLog.w(TAG, "connectAudio 发起失败（可能非音频设备），直接尝试建立 SPP: " + audioRes.getFailure());
                    onClassicLinkEstablished(currentGen, deviceId, listener);
                }
            }
        }
    }

    private void onClassicLinkEstablished(int gen, @NonNull CbtDeviceId deviceId, @NonNull SppStateListener listener) {
        synchronized (lock) {
            if (gen != connectGeneration || classicConnectSettled) {
                return;
            }
            classicConnectSettled = true;
            cancelClassicObserversLocked();
        }
        WyLog.i(TAG, "经典蓝牙链路已建立，开始发起 SPP 双通道连接: " + deviceId.value());
        connectSppChannels(gen, deviceId, listener);
    }

    private void handleClassicTimeout(int gen, @NonNull SppStateListener listener) {
        synchronized (lock) {
            if (gen != connectGeneration || classicConnectSettled) {
                return;
            }
            classicConnectSettled = true;
            cancelClassicObserversLocked();
        }
        WyLog.w(TAG, "经典蓝牙连接超时");
        listener.onControlStateChanged(SppState.FAILED, WyControl.Failure.TIMEOUT);
    }

    private void handleClassicFailed(int gen, @NonNull SppStateListener listener, @NonNull WyControl.Failure failure) {
        synchronized (lock) {
            if (gen != connectGeneration || classicConnectSettled) {
                return;
            }
            classicConnectSettled = true;
            cancelClassicObserversLocked();
        }
        WyLog.w(TAG, "经典蓝牙连接失败: " + failure);
        listener.onControlStateChanged(SppState.FAILED, failure);
    }

    private void cancelClassicObserversLocked() {
        if (classicTimeoutRunnable != null) {
            delay.removeCallbacks(classicTimeoutRunnable);
            classicTimeoutRunnable = null;
        }
        if (bondHandle != null) {
            bondHandle.cancel();
            bondHandle = null;
        }
        if (aclHandle != null) {
            aclHandle.cancel();
            aclHandle = null;
        }
        if (profileHandle != null) {
            profileHandle.cancel();
            profileHandle = null;
        }
    }

    private boolean isBonded(@NonNull CbtDeviceId deviceId) {
        Result<List<CbtDeviceSnapshot>> bonded = client.getBondedDevices();
        if (bonded.isSuccess() && bonded.getValue() != null) {
            for (CbtDeviceSnapshot snapshot : bonded.getValue()) {
                if (snapshot.deviceId() != null && deviceId.equals(snapshot.deviceId())) {
                    return snapshot.bondState() == CbtBondState.BONDED;
                }
            }
        }
        return false;
    }

    private void connectSppChannels(int gen, @NonNull CbtDeviceId deviceId, @NonNull SppStateListener listener) {
        synchronized (lock) {
            if (gen != connectGeneration) {
                return;
            }
            controlConnected = false;
            audioConnected = false;

            CbtSppConfig config = CbtSppConfig.builder()
                    .connectTimeoutMillis(15000L)
                    .build();

            // 1. 连接控制通道
            controlHandle = client.connectSpp(deviceId, CbtSppEndpoint.uuid(CONTROL_UUID), config, new CbtSppConnectionObserver() {
                @Override
                public void onSppStateChanged(@NonNull CbtSppConnectionSnapshot snapshot) {
                    synchronized (lock) {
                        if (gen != connectGeneration) return;
                        if (snapshot.state() == CbtSppConnectionState.CONNECTED) {
                            controlConnectionId = snapshot.connectionId();
                            controlConnected = true;
                        } else if (snapshot.state() == CbtSppConnectionState.DISCONNECTED
                                || snapshot.state() == CbtSppConnectionState.FAILED) {
                            controlConnectionId = null;
                            controlConnected = false;
                        }
                    }
                    SppState state = mapSppState(snapshot.state());
                    WyControl.Failure failure = snapshot.failure() != null ? mapFailure(snapshot.failure()) : null;
                    listener.onControlStateChanged(state, failure);
                }

                @Override
                public void onSppDataReceived(@NonNull CbtSppConnectionId connectionId, @NonNull byte[] data) {
                    listener.onControlDataReceived(data);
                }
            });

            // 2. 连接音频通道
            audioHandle = client.connectSpp(deviceId, CbtSppEndpoint.uuid(AUDIO_UUID), config, new CbtSppConnectionObserver() {
                @Override
                public void onSppStateChanged(@NonNull CbtSppConnectionSnapshot snapshot) {
                    synchronized (lock) {
                        if (gen != connectGeneration) return;
                        if (snapshot.state() == CbtSppConnectionState.CONNECTED) {
                            audioConnectionId = snapshot.connectionId();
                            audioConnected = true;
                        } else if (snapshot.state() == CbtSppConnectionState.DISCONNECTED
                                || snapshot.state() == CbtSppConnectionState.FAILED) {
                            audioConnectionId = null;
                            audioConnected = false;
                        }
                    }
                    SppState state = mapSppState(snapshot.state());
                    WyControl.Failure failure = snapshot.failure() != null ? mapFailure(snapshot.failure()) : null;
                    listener.onAudioStateChanged(state, failure);
                }

                @Override
                public void onSppDataReceived(@NonNull CbtSppConnectionId connectionId, @NonNull byte[] data) {
                    listener.onAudioDataReceived(data);
                }
            });
        }
    }

    @Override
    public void disconnect() {
        synchronized (lock) {
            connectGeneration++;
            classicConnectSettled = true;
            cancelClassicObserversLocked();
            if (controlHandle != null) {
                controlHandle.cancel();
                controlHandle = null;
            }
            if (audioHandle != null) {
                audioHandle.cancel();
                audioHandle = null;
            }
            if (controlConnectionId != null) {
                client.disconnectSpp(controlConnectionId);
                controlConnectionId = null;
            }
            if (audioConnectionId != null) {
                client.disconnectSpp(audioConnectionId);
                audioConnectionId = null;
            }
            controlConnected = false;
            audioConnected = false;
        }
    }

    @Override
    public void sendControl(@NonNull byte[] data, @NonNull SendCallback callback) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(callback, "callback");
        CbtSppConnectionId connId;
        synchronized (lock) {
            connId = controlConnectionId;
        }
        if (connId == null) {
            callback.onComplete(false, WyControl.Failure.DISCONNECTED);
            return;
        }
        Result<Void> result = client.send(connId, data);
        if (result.isSuccess()) {
            callback.onComplete(true, null);
        } else {
            callback.onComplete(false, result.getFailure() != null ? mapFailure(result.getFailure()) : WyControl.Failure.DEVICE_ERROR);
        }
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
        CbtDeviceId deviceId = CbtDeviceId.fromAddress(classicAddress);
        Result<CbtAclLinkState> acl = client.getAclState(deviceId);
        if (acl.isSuccess() && acl.getValue() == CbtAclLinkState.CONNECTED) {
            return true;
        }
        Result<List<CbtProfileType>> profiles = client.getConnectedProfiles(deviceId);
        if (profiles.isSuccess() && profiles.getValue() != null && !profiles.getValue().isEmpty()) {
            return true;
        }
        return false;
    }

    @Override
    public void release() {
        disconnect();
        stopDiscovery();
    }

    private static SppState mapSppState(CbtSppConnectionState state) {
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

    private static WyControl.Failure mapFailure(com.yovo.lib_bluetooth.model.common.Failure failure) {
        if (failure == null) {
            return WyControl.Failure.INTERNAL_ERROR;
        }
        String code = failure.code();
        if (CbtErrorCode.BLUETOOTH_DISABLED.name().equals(code)) {
            return WyControl.Failure.BLUETOOTH_DISABLED;
        }
        if (CbtErrorCode.PERMISSION_DENIED.name().equals(code)) {
            return WyControl.Failure.PERMISSION_DENIED;
        }
        if (CbtErrorCode.DISCOVERY_START_FAILED.name().equals(code)) {
            return WyControl.Failure.SCAN_FAILED;
        }
        if (CbtErrorCode.SPP_CONNECT_TIMEOUT.name().equals(code)) {
            return WyControl.Failure.TIMEOUT;
        }
        if (CbtErrorCode.SPP_LOCAL_CLOSED.name().equals(code) || CbtErrorCode.SPP_REMOTE_CLOSED.name().equals(code)) {
            return WyControl.Failure.DISCONNECTED;
        }
        return WyControl.Failure.DEVICE_ERROR;
    }
}
