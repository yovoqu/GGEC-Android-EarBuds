package com.yovo.biz_ggec_wy.internal.link;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_ggec_wy.api.WyControl;
import com.yovo.biz_ggec_wy.api.WyDevice;
import com.yovo.biz_ggec_wy.internal.center.catalog.WyCatalogImpl;
import com.yovo.biz_ggec_wy.internal.center.port.WyClassicRadioPort;
import com.yovo.biz_ggec_wy.internal.support.HexPreview;
import com.yovo.biz_ggec_wy.provider.lib_common.log.WyLog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * WY 会话管理：管理控制通道与音频通道两条 SPP 链路的创建、连接、收发、音频路由与关闭。
 */
public final class WySession {

    private static final String TAG = "WySession";
    private static final long BATTERY_QUERY_TIMEOUT_MS = 5000L;

    public interface MainDispatcher {
        void post(@NonNull Runnable r);

        default void postDelayed(@NonNull Runnable r, long delayMillis) {
        }

        default void removeCallbacks(@NonNull Runnable r) {
        }
    }

    private static final class PendingBatteryQuery {
        final int partId;
        final WyControl.Callback callback;
        Runnable timeoutTask;

        PendingBatteryQuery(int partId, WyControl.Callback callback) {
            this.partId = partId;
            this.callback = callback;
        }
    }

    private final WyClassicRadioPort radio;
    private final WyCatalogImpl catalog;
    private final MainDispatcher mainDispatcher;
    private final CopyOnWriteArrayList<WyControl.Listener> observers = new CopyOnWriteArrayList<>();

    private final Object batteryLock = new Object();
    private final List<PendingBatteryQuery> pendingBatteryQueries = new ArrayList<>();
    private final WyBatteryProtocol.FrameAccumulator frameAccumulator = new WyBatteryProtocol.FrameAccumulator();

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
            return new MainDispatcher() {
                @Override
                public void post(@NonNull Runnable r) {
                    handler.post(r);
                }

                @Override
                public void postDelayed(@NonNull Runnable r, long delayMillis) {
                    handler.postDelayed(r, delayMillis);
                }

                @Override
                public void removeCallbacks(@NonNull Runnable r) {
                    handler.removeCallbacks(r);
                }
            };
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
        resetSessionData(WyControl.Failure.DISCONNECTED);
        synchronized (values) {
            values.clear();
        }
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
                // Biz 层处理 SPP 控制数据时打印真实原始字节（lib 不打印数据）。
                WyLog.d(TAG, "SPP接收控制命令数据: " + HexPreview.of(data, 48));
                handleControlData(data);
            }

            @Override
            public void onAudioDataReceived(@NonNull byte[] data) {
                // Biz 层处理 SPP 音频数据时打印真实原始字节（lib 不打印数据）。
                WyLog.d(TAG, "SPP接收音频数据: " + HexPreview.of(data, 48));
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
                // 连接就绪后主动查询一次三部分电量（99 EC 80 00 01 00 12 34）
                queryBattery(WyBatteryProtocol.PART_ALL, null);
            }
        } else if (state == WyClassicRadioPort.SppState.DISCONNECTED || state == WyClassicRadioPort.SppState.FAILED) {
            if (sessionState.state() != WyControl.State.DISCONNECTED && sessionState.state() != WyControl.State.IDLE) {
                sessionState = WySessionState.disconnected(failure);
                WyLog.w(TAG, "控制流 SPP 连接断开: " + failure);
                resetSessionData(failure != null ? failure : WyControl.Failure.DISCONNECTED);
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
        List<byte[]> frames = frameAccumulator.append(data);
        for (byte[] frame : frames) {
            Optional<WyBatteryProtocol.BatteryUpdate> batteryUpdate = WyBatteryProtocol.parseFrame(frame);
            if (batteryUpdate.isPresent()) {
                handleBatteryUpdate(batteryUpdate.get());
            }
        }
        Consumer<byte[]> consumer = controlDataConsumer;
        if (consumer != null && data != null && data.length > 0) {
            consumer.accept(data);
        }
    }

    public void disconnect() {
        WyLog.i(TAG, "主动断开连接");
        radio.disconnect();
        sessionState = WySessionState.disconnected(null);
        resetSessionData(WyControl.Failure.DISCONNECTED);
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
        // WY 写入是本地即时受理：先回调「已受理/处理中」，再回调终态。
        callback.onStarted();
        callback.onResult(new WyControl.Outcome.Ok<>(null));
    }

    public void perform(@NonNull WyControl.Action action, @NonNull WyControl.Callback callback) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(callback, "callback");
        if (sessionState.state() != WyControl.State.READY) {
            callback.onResult(new WyControl.Outcome.Err<>(WyControl.Failure.DISCONNECTED));
            return;
        }
        if (action instanceof WyControl.Action.SyncDeviceState) {
            // 同步设备数据对于 WY01 就是直接调用一次三部分电量查询协议
            queryBattery(WyBatteryProtocol.PART_ALL, callback);
            return;
        }
        callback.onResult(new WyControl.Outcome.Ok<>(null));
    }

    /**
     * 查询 WY01 电量：
     * {@code PART_ALL(0x00)} 发送 {@code 99 EC 80 00 01 00 12 34}；
     * {@code PART_LEFT(0x01)/PART_RIGHT(0x02)/PART_CASE(0x03)} 发送 {@code 99 EC 80 00 01 01/02/03 12 34}。
     */
    public void queryBattery(int partId, @Nullable WyControl.Callback callback) {
        if (sessionState.state() != WyControl.State.READY) {
            if (callback != null) {
                callback.onResult(new WyControl.Outcome.Err<>(WyControl.Failure.DISCONNECTED));
            }
            return;
        }
        byte[] frame = WyBatteryProtocol.queryFrame(partId);
        PendingBatteryQuery pending = callback != null ? new PendingBatteryQuery(partId, callback) : null;
        if (pending != null) {
            Runnable timeoutTask = () -> {
                boolean removed;
                synchronized (batteryLock) {
                    removed = pendingBatteryQueries.remove(pending);
                }
                if (removed) {
                    WyLog.w(TAG, "电量查询超时, partId=0x" + Integer.toHexString(partId));
                    pending.callback.onResult(new WyControl.Outcome.Err<>(WyControl.Failure.TIMEOUT));
                }
            };
            pending.timeoutTask = timeoutTask;
            synchronized (batteryLock) {
                pendingBatteryQueries.add(pending);
            }
            mainDispatcher.postDelayed(timeoutTask, BATTERY_QUERY_TIMEOUT_MS);
        }

        radio.sendControl(frame, (success, failure) -> {
            if (success) {
                if (pending != null) {
                    pending.callback.onStarted();
                }
                return;
            }
            if (pending != null) {
                boolean removed;
                synchronized (batteryLock) {
                    removed = pendingBatteryQueries.remove(pending);
                }
                if (removed) {
                    mainDispatcher.removeCallbacks(pending.timeoutTask);
                    pending.callback.onResult(new WyControl.Outcome.Err<>(
                            failure != null ? failure : WyControl.Failure.DEVICE_ERROR));
                }
            }
        });
    }

    private void handleBatteryUpdate(@NonNull WyBatteryProtocol.BatteryUpdate update) {
        WyDevice.Battery previous;
        WyDevice.Battery updated;
        List<PendingBatteryQuery> completed = new ArrayList<>();
        synchronized (batteryLock) {
            previous = this.battery;
            updated = WyBatteryProtocol.applyUpdate(previous, update);
            this.battery = updated;

            Iterator<PendingBatteryQuery> it = pendingBatteryQueries.iterator();
            while (it.hasNext()) {
                PendingBatteryQuery query = it.next();
                boolean matches = update instanceof WyBatteryProtocol.BatteryUpdate.All
                        || (update instanceof WyBatteryProtocol.BatteryUpdate.Single single
                        && query.partId == single.partId());
                if (matches) {
                    it.remove();
                    completed.add(query);
                }
            }
        }

        for (PendingBatteryQuery query : completed) {
            mainDispatcher.removeCallbacks(query.timeoutTask);
            query.callback.onResult(new WyControl.Outcome.Ok<>(null));
        }

        if (!updated.equals(previous)) {
            WyLog.i(TAG, "WY01 电量更新: left=" + updated.leftLevel()
                    + ", right=" + updated.rightLevel()
                    + ", case=" + updated.caseLevel());
            dispatchDeviceEvent(new WyControl.Event.BatteryChanged(updated));
            dispatchSnapshotChanged();
        }
    }

    private void resetSessionData(@NonNull WyControl.Failure failure) {
        frameAccumulator.reset();
        List<PendingBatteryQuery> cancelled;
        synchronized (batteryLock) {
            this.battery = WyDevice.Battery.unknown();
            this.deviceInfo = WyDevice.Info.unknown();
            cancelled = new ArrayList<>(pendingBatteryQueries);
            pendingBatteryQueries.clear();
        }
        for (PendingBatteryQuery query : cancelled) {
            mainDispatcher.removeCallbacks(query.timeoutTask);
            query.callback.onResult(new WyControl.Outcome.Err<>(failure));
        }
    }

    private void dispatchDeviceEvent(@NonNull WyControl.Event event) {
        mainDispatcher.post(() -> {
            for (WyControl.Listener observer : observers) {
                observer.onDeviceEvent(event);
            }
        });
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
