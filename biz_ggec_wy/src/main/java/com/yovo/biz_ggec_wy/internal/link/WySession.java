package com.yovo.biz_ggec_wy.internal.link;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_ggec_wy.api.WyControl;
import com.yovo.biz_ggec_wy.api.WyDevice;
import com.yovo.biz_ggec_wy.internal.center.catalog.WyCatalogImpl;
import com.yovo.biz_ggec_wy.internal.center.port.WyClassicRadioPort;
import com.yovo.biz_ggec_wy.provider.lib_common.log.WyLog;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * WY 会话管理：管理控制通道与音频通道两条 SPP 链路的创建、连接、收发、音频路由与关闭。
 */
public final class WySession {

    private static final String TAG = "WySession";

    public interface MainDispatcher {
        void post(@NonNull Runnable r);
    }

    private final WyClassicRadioPort radio;
    private final WyCatalogImpl catalog;
    private final MainDispatcher mainDispatcher;
    private final CopyOnWriteArrayList<WyControl.Listener> observers = new CopyOnWriteArrayList<>();

    private volatile WySessionState sessionState = WySessionState.idle();
    private volatile WyControl.Peer peer = WyControl.Peer.none();
    private volatile WyDevice.Battery battery = WyDevice.Battery.unknown();
    private volatile WyDevice.Info deviceInfo = WyDevice.Info.unknown();
    private final Map<WyDevice.Capability, WyControl.Value> values = new LinkedHashMap<>();

    @Nullable
    private volatile Consumer<byte[]> audioChunkConsumer;
    @Nullable
    private volatile Consumer<byte[]> controlDataConsumer;

    public WySession(@NonNull WyClassicRadioPort radio,
                     @NonNull WyCatalogImpl catalog) {
        this(radio, catalog, createDefaultDispatcher());
    }

    public WySession(@NonNull WyClassicRadioPort radio,
                     @NonNull WyCatalogImpl catalog,
                     @NonNull MainDispatcher mainDispatcher) {
        this.radio = Objects.requireNonNull(radio, "radio");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.mainDispatcher = Objects.requireNonNull(mainDispatcher, "mainDispatcher");
    }

    private static MainDispatcher createDefaultDispatcher() {
        if (Looper.getMainLooper() != null) {
            Handler handler = new Handler(Looper.getMainLooper());
            return handler::post;
        }
        return Runnable::run;
    }

    public void setAudioChunkConsumer(@Nullable Consumer<byte[]> consumer) {
        this.audioChunkConsumer = consumer;
    }

    public void setControlDataConsumer(@Nullable Consumer<byte[]> consumer) {
        this.controlDataConsumer = consumer;
    }

    @NonNull
    public WyControl.State connectionState() {
        return sessionState.state();
    }

    @NonNull
    public WyControl.Peer peer() {
        return peer;
    }

    public void observe(@NonNull WyControl.Listener listener) {
        Objects.requireNonNull(listener, "listener");
        observers.addIfAbsent(listener);
    }

    public void removeObserver(@NonNull WyControl.Listener listener) {
        Objects.requireNonNull(listener, "listener");
        observers.remove(listener);
    }

    @NonNull
    public WyDevice.Snapshot snapshot() {
        Map<WyDevice.Capability, WyControl.Value> copy;
        synchronized (values) {
            copy = new LinkedHashMap<>(values);
        }
        return new WyDevice.Snapshot(sessionState.state(), battery, deviceInfo, Collections.unmodifiableMap(copy));
    }

    public void connect(@NonNull String modelId, @NonNull WyDevice.Radios radios) {
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(radios, "radios");
        String address = radios.classicAddress();
        if (address.isBlank()) {
            dispatchConnectionChanged(WyControl.State.DISCONNECTED, WyControl.Failure.INTERNAL_ERROR);
            return;
        }

        this.peer = new WyControl.Peer(modelId, radios);
        this.sessionState = WySessionState.connecting();
        dispatchConnectionChanged(WyControl.State.CONNECTING, null);
        WyLog.i(TAG, "发起双通道 SPP 连接: " + modelId + " " + address);

        radio.connect(address, new WyClassicRadioPort.SppStateListener() {
            @Override
            public void onControlStateChanged(@NonNull WyClassicRadioPort.SppState state,
                                              @Nullable WyControl.Failure failure) {
                WyLog.i(TAG, "控制流 SPP 状态变更: " + state + ", failure=" + failure);
                handleControlSppState(state, failure);
            }

            @Override
            public void onAudioStateChanged(@NonNull WyClassicRadioPort.SppState state,
                                            @Nullable WyControl.Failure failure) {
                WyLog.i(TAG, "音频流 SPP 状态变更: " + state + ", failure=" + failure);
                handleAudioSppState(state, failure);
            }

            @Override
            public void onControlDataReceived(@NonNull byte[] data) {
                handleControlData(data);
            }

            @Override
            public void onAudioDataReceived(@NonNull byte[] data) {
                Consumer<byte[]> consumer = audioChunkConsumer;
                if (consumer != null && data != null && data.length > 0) {
                    consumer.accept(data);
                }
            }
        });
    }

    private synchronized void handleControlSppState(@NonNull WyClassicRadioPort.SppState state,
                                                    @Nullable WyControl.Failure failure) {
        if (state == WyClassicRadioPort.SppState.CONNECTED) {
            // 控制流 SPP 连接就绪
            if (radio.isControlConnected() && sessionState.state() != WyControl.State.READY) {
                sessionState = WySessionState.ready();
                WyLog.i(TAG, "控制流 SPP 就绪 (Ready)");
                dispatchConnectionChanged(WyControl.State.READY, null);
            }
        } else if (state == WyClassicRadioPort.SppState.DISCONNECTED || state == WyClassicRadioPort.SppState.FAILED) {
            if (sessionState.state() != WyControl.State.DISCONNECTED && sessionState.state() != WyControl.State.IDLE) {
                sessionState = WySessionState.disconnected(failure);
                WyLog.w(TAG, "控制流 SPP 连接断开: " + failure);
                radio.disconnectAudioSpp();
                dispatchConnectionChanged(WyControl.State.DISCONNECTED, failure != null ? failure : WyControl.Failure.DISCONNECTED);
            }
        }
    }

    private void handleAudioSppState(@NonNull WyClassicRadioPort.SppState state,
                                     @Nullable WyControl.Failure failure) {
        // 音频流生命周期随录音业务按需拉起与断开，不影响全局会话连接状态
        WyLog.d(TAG, "音频流 SPP 通道状态: " + state + (failure != null ? (", failure=" + failure) : ""));
    }

    private void handleControlData(@NonNull byte[] data) {
        // 控制流下行协议解析
        WyLog.d(TAG, "收到控制流数据长度: " + data.length);
        Consumer<byte[]> consumer = controlDataConsumer;
        if (consumer != null && data != null && data.length > 0) {
            consumer.accept(data);
        }
    }

    public void disconnect() {
        WyLog.i(TAG, "主动断开连接");
        radio.disconnect();
        sessionState = WySessionState.disconnected(null);
        dispatchConnectionChanged(WyControl.State.DISCONNECTED, null);
    }

    public void close() {
        disconnect();
        radio.release();
        observers.clear();
    }

    @NonNull
    public WyControl.Outcome<WyControl.Value> read(@NonNull WyDevice.Capability capability) {
        Objects.requireNonNull(capability, "capability");
        if (sessionState.state() != WyControl.State.READY) {
            return new WyControl.Outcome.Err<>(WyControl.Failure.DISCONNECTED);
        }
        if (!catalog.supports(peer.modelId(), capability)) {
            return new WyControl.Outcome.Err<>(WyControl.Failure.UNSUPPORTED_CAPABILITY);
        }
        synchronized (values) {
            WyControl.Value val = values.get(capability);
            if (val != null) {
                return new WyControl.Outcome.Ok<>(val);
            }
        }
        return new WyControl.Outcome.Err<>(WyControl.Failure.DEVICE_ERROR);
    }

    public void write(@NonNull WyDevice.Capability capability,
                      @NonNull WyControl.Value value,
                      @NonNull WyControl.Callback callback) {
        Objects.requireNonNull(capability, "capability");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(callback, "callback");
        if (sessionState.state() != WyControl.State.READY) {
            callback.onResult(new WyControl.Outcome.Err<>(WyControl.Failure.DISCONNECTED));
            return;
        }
        if (!catalog.supports(peer.modelId(), capability)) {
            callback.onResult(new WyControl.Outcome.Err<>(WyControl.Failure.UNSUPPORTED_CAPABILITY));
            return;
        }
        synchronized (values) {
            values.put(capability, value);
        }
        dispatchSnapshotChanged();
        callback.onResult(new WyControl.Outcome.Ok<>(null));
    }

    public void perform(@NonNull WyControl.Action action, @NonNull WyControl.Callback callback) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(callback, "callback");
        if (sessionState.state() != WyControl.State.READY) {
            callback.onResult(new WyControl.Outcome.Err<>(WyControl.Failure.DISCONNECTED));
            return;
        }
        callback.onResult(new WyControl.Outcome.Ok<>(null));
    }

    private void dispatchConnectionChanged(WyControl.State state, WyControl.Failure failure) {
        mainDispatcher.post(() -> {
            for (WyControl.Listener observer : observers) {
                observer.onConnectionChanged(state, failure);
            }
        });
    }

    private void dispatchSnapshotChanged() {
        WyDevice.Snapshot snap = snapshot();
        mainDispatcher.post(() -> {
            for (WyControl.Listener observer : observers) {
                observer.onSnapshotChanged(snap);
            }
        });
    }
}
