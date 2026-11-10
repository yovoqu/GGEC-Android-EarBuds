package com.yovo.lib_bluetooth.internal.ble.connection;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.ble.device.YoBleCapabilityInspector;
import com.yovo.lib_bluetooth.internal.ble.device.YoBleDeviceSnapshotStore;
import com.yovo.lib_bluetooth.internal.ble.diagnostics.YoBleTimelineRecorder;
import com.yovo.lib_bluetooth.internal.ble.error.YoBleErrorMapper;
import com.yovo.lib_bluetooth.internal.ble.pairing.YoBleBondController;
import com.yovo.lib_bluetooth.internal.ble.runtime.YoBleRuntime;
import com.yovo.lib_bluetooth.internal.common.lifecycle.YoReleasable;
import com.yovo.lib_bluetooth.model.ble.BleBondState;
import com.yovo.lib_bluetooth.model.ble.BleConfig;
import com.yovo.lib_bluetooth.model.ble.BleConnectionConfig;
import com.yovo.lib_bluetooth.model.ble.BleConnectionObserver;
import com.yovo.lib_bluetooth.model.ble.BleConnectionPriority;
import com.yovo.lib_bluetooth.model.ble.BleConnectionSnapshot;
import com.yovo.lib_bluetooth.model.ble.BleConnectionState;
import com.yovo.lib_bluetooth.model.ble.BleDataSplitter;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleDeviceSnapshot;
import com.yovo.lib_bluetooth.model.ble.BleErrorCode;
import com.yovo.lib_bluetooth.model.ble.BleFailure;
import com.yovo.lib_bluetooth.model.ble.BleGattDatabaseSnapshot;
import com.yovo.lib_bluetooth.model.ble.BleGattObserver;
import com.yovo.lib_bluetooth.model.ble.BleGattTarget;
import com.yovo.lib_bluetooth.model.ble.BleNotificationObserver;
import com.yovo.lib_bluetooth.model.ble.BleNotificationSetupMode;
import com.yovo.lib_bluetooth.model.ble.BleOperationHandle;
import com.yovo.lib_bluetooth.model.ble.BleOperationPriority;
import com.yovo.lib_bluetooth.model.ble.BlePhy;
import com.yovo.lib_bluetooth.model.ble.BlePhyConfig;
import com.yovo.lib_bluetooth.model.ble.BlePhyOptions;
import com.yovo.lib_bluetooth.model.ble.BleRequestResponseConfig;
import com.yovo.lib_bluetooth.model.ble.BleTransferProgressObserver;
import com.yovo.lib_bluetooth.model.ble.BleValueObserver;
import com.yovo.lib_bluetooth.model.ble.BleWriteType;
import com.yovo.lib_bluetooth.model.common.Result;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * 多设备会话表。表的写入只发生在 state 线程，跨线程只读已发布的快照和队列长度。
 */
public final class YoBleConnectionManager implements YoReleasable {

    private static final String MODULE = "ble-connection-mgr";
    private static final int ENDED_SESSION_CAP = 16;

    private final YoBleRuntime runtime;
    private final BleConfig bleConfig;
    private final YoBleCapabilityInspector capabilityInspector;
    private final YoBleConnectionPlatform connectionPlatform;
    private final YoBleBondController bondController;
    private final YoBleDeviceSnapshotStore snapshotStore;
    private final YoBleTimelineRecorder timeline;
    private final LongSupplier clockMillis;

    private final ConcurrentHashMap<BleDeviceId, YoBleGattSession> sessions = new ConcurrentHashMap<>();
    private final Map<BleDeviceId, Boolean> endedOrder = new LinkedHashMap<>(16, 0.75f, true);
    private final ConcurrentHashMap<BleDeviceId, Long> lastClosedAtMillis = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<BleConnectionObserver> globalObservers = new CopyOnWriteArrayList<>();
    private final AtomicLong opSeq = new AtomicLong();
    private volatile boolean released;

    public YoBleConnectionManager(@NonNull YoBleRuntime runtime,
                                  @NonNull BleConfig bleConfig,
                                  @NonNull YoBleCapabilityInspector capabilityInspector,
                                  @NonNull YoBleConnectionPlatform connectionPlatform,
                                  @NonNull YoBleBondController bondController,
                                  @NonNull YoBleDeviceSnapshotStore snapshotStore,
                                  @NonNull YoBleTimelineRecorder timeline,
                                  @NonNull LongSupplier clockMillis) {
        this.runtime = runtime;
        this.bleConfig = bleConfig;
        this.capabilityInspector = capabilityInspector;
        this.connectionPlatform = connectionPlatform;
        this.bondController = bondController;
        this.snapshotStore = snapshotStore;
        this.timeline = timeline;
        this.clockMillis = clockMillis;
    }

    @NonNull
    public BleOperationHandle connect(@NonNull BleDeviceId deviceId,
                                      @NonNull BleConnectionConfig config,
                                      @NonNull BleConnectionObserver observer) {
        String opId = "ble-connect-" + opSeq.incrementAndGet();
        AtomicBoolean cancelled = new AtomicBoolean(false);
        runtime.state(() -> {
            if (cancelled.get()) {
                return;
            }
            BleFailure preflight = checkConnectPreflight(deviceId);
            if (preflight != null) {
                runtime.callback(() -> observer.onConnectionFailed(preflight));
                return;
            }
            YoBleGattSession existing = sessions.get(deviceId);
            if (existing != null && existing.isLive()) {
                existing.attachObserver(observer);
                replay(existing, observer);
                return;
            }
            if (existing != null && existing.isDisconnecting()) {
                existing.whenEnded(() -> {
                    if (!cancelled.get()) {
                        openSession(deviceId, config, observer);
                    }
                });
                return;
            }
            openSession(deviceId, config, observer);
        });
        return new BleOperationHandle(opId, () -> runtime.state(() -> {
            if (!cancelled.compareAndSet(false, true)) {
                return;
            }
            YoBleGattSession session = sessions.get(deviceId);
            if (session == null || session.isEnded()) {
                return;
            }
            session.detachObserver(observer);
            if (!session.isReady() && session.observerCount() == 0) {
                session.userDisconnect();
            }
        }));
    }

    @NonNull
    public Result<Void> reconnect(@NonNull BleDeviceId deviceId) {
        BleFailure preflight = checkConnectPreflight(deviceId);
        if (preflight != null) {
            return Result.failure(preflight);
        }
        if (!sessions.containsKey(deviceId)) {
            return Result.failure(missing(deviceId));
        }
        runtime.state(() -> {
            YoBleGattSession session = sessions.get(deviceId);
            if (session != null && session.isEnded()) {
                endedOrder.remove(deviceId);
                session.startConnect();
            }
        });
        return Result.success(null);
    }

    @NonNull
    public Result<Void> disconnect(@NonNull BleDeviceId deviceId) {
        if (released) {
            return Result.failure(releasedFailure(deviceId));
        }
        runtime.state(() -> {
            YoBleGattSession session = sessions.get(deviceId);
            if (session != null && !session.isEnded()) {
                session.userDisconnect();
            }
        });
        return Result.success(null);
    }

    @NonNull
    public Result<Void> disconnectAll() {
        if (released) {
            return Result.failure(releasedFailure(null));
        }
        runtime.state(() -> {
            for (YoBleGattSession session : sessions.values()) {
                if (!session.isEnded()) {
                    session.userDisconnect();
                }
            }
        });
        return Result.success(null);
    }

    @NonNull
    public Result<BleConnectionSnapshot> getConnectionSnapshot(@NonNull BleDeviceId deviceId) {
        if (!BleDeviceId.isValid(deviceId.value())) {
            return Result.failure(YoBleErrorMapper.failure(
                    BleErrorCode.DEVICE_INVALID, MODULE, "Invalid MAC address: " + deviceId, deviceId));
        }
        BleDeviceSnapshot stored = snapshotStore.get(deviceId);
        if (stored != null && stored.connectionSnapshot() != null) {
            return Result.success(stored.connectionSnapshot());
        }
        return Result.success(new BleConnectionSnapshot(deviceId, BleConnectionState.IDLE, clockMillis.getAsLong()));
    }

    @NonNull
    public Result<List<BleConnectionSnapshot>> getConnectedDevices() {
        if (released) {
            return Result.failure(releasedFailure(null));
        }
        List<BleConnectionSnapshot> connected = new ArrayList<>();
        for (BleDeviceSnapshot device : snapshotStore.all()) {
            BleConnectionSnapshot connection = device.connectionSnapshot();
            if (connection != null && isLinkUp(connection.state())) {
                connected.add(connection);
            }
        }
        return Result.success(connected);
    }

    @NonNull
    public List<BleConnectionSnapshot> exportActiveSnapshots() {
        List<BleConnectionSnapshot> active = new ArrayList<>();
        for (BleDeviceSnapshot device : snapshotStore.all()) {
            BleConnectionSnapshot connection = device.connectionSnapshot();
            if (connection != null && connection.state() != BleConnectionState.IDLE
                    && connection.state() != BleConnectionState.DISCONNECTED
                    && connection.state() != BleConnectionState.FAILED) {
                active.add(connection);
            }
        }
        return active;
    }

    public int pendingCount(@NonNull BleDeviceId deviceId) {
        YoBleGattSession session = sessions.get(deviceId);
        return session == null ? 0 : session.publishedPendingCount();
    }

    @NonNull
    public BleOperationHandle observeConnectionState(@NonNull BleConnectionObserver observer) {
        String opId = "ble-conn-obs-" + opSeq.incrementAndGet();
        globalObservers.addIfAbsent(observer);
        return new BleOperationHandle(opId, () -> globalObservers.remove(observer));
    }

    @NonNull
    public BleOperationHandle requestMtu(@NonNull BleDeviceId deviceId,
                                         int mtu,
                                         @NonNull BleValueObserver<Integer> observer) {
        return onSession(deviceId, observer::onFailed, session -> session.linkTuner().requestMtu(mtu, hop(observer)));
    }

    @NonNull
    public Result<Integer> overrideMtu(@NonNull BleDeviceId deviceId, int mtu) {
        if (!isLinkUp(publishedState(deviceId))) {
            return Result.failure(missing(deviceId));
        }
        int clamped = Math.max(23, Math.min(517, mtu));
        runtime.state(() -> {
            YoBleGattSession session = sessions.get(deviceId);
            if (session != null) {
                session.linkTuner().overrideMtu(clamped);
            }
        });
        return Result.success(clamped);
    }

    @NonNull
    public BleOperationHandle requestConnectionPriority(@NonNull BleDeviceId deviceId,
                                                        @NonNull BleConnectionPriority priority,
                                                        @NonNull BleValueObserver<Void> observer) {
        return onSession(deviceId, observer::onFailed,
                session -> session.linkTuner().requestConnectionPriority(priority, hop(observer)));
    }

    @NonNull
    public BleOperationHandle setPreferredPhy(@NonNull BleDeviceId deviceId,
                                              @NonNull BlePhy txPhy,
                                              @NonNull BlePhy rxPhy,
                                              @NonNull BlePhyOptions phyOptions,
                                              @NonNull BleValueObserver<BlePhyConfig> observer) {
        return onSession(deviceId, observer::onFailed,
                session -> session.linkTuner().setPreferredPhy(txPhy, rxPhy, phyOptions, hop(observer)));
    }

    @NonNull
    public BleOperationHandle readPhy(@NonNull BleDeviceId deviceId,
                                      @NonNull BleValueObserver<BlePhyConfig> observer) {
        return onSession(deviceId, observer::onFailed, session -> session.linkTuner().readPhy(hop(observer)));
    }

    @NonNull
    public BleOperationHandle readRemoteRssi(@NonNull BleDeviceId deviceId,
                                             @NonNull BleValueObserver<Integer> observer) {
        return onSession(deviceId, observer::onFailed, session -> session.linkTuner().readRemoteRssi(hop(observer)));
    }

    @NonNull
    public BleOperationHandle discoverServices(@NonNull BleDeviceId deviceId,
                                               boolean forceRediscover,
                                               @NonNull BleValueObserver<BleGattDatabaseSnapshot> observer) {
        return onSession(deviceId, observer::onFailed,
                session -> session.serviceDiscoveryManager().discover(forceRediscover, false, hop(observer)));
    }

    @NonNull
    public Result<BleGattDatabaseSnapshot> getServices(@NonNull BleDeviceId deviceId) {
        BleDeviceSnapshot stored = snapshotStore.get(deviceId);
        if (stored == null || stored.gattDatabase() == null) {
            return Result.failure(YoBleErrorMapper.failure(
                    BleErrorCode.SERVICE_NOT_FOUND, MODULE, "services not discovered", deviceId));
        }
        return Result.success(stored.gattDatabase());
    }

    @NonNull
    public BleOperationHandle refreshDeviceCache(@NonNull BleDeviceId deviceId,
                                                 @NonNull BleValueObserver<BleGattDatabaseSnapshot> observer) {
        return onSession(deviceId, observer::onFailed,
                session -> session.serviceDiscoveryManager().discover(true, true, hop(observer)));
    }

    @NonNull
    public BleOperationHandle read(@NonNull BleDeviceId deviceId,
                                   @NonNull BleGattTarget target,
                                   @NonNull BleOperationPriority priority,
                                   @NonNull BleGattObserver observer) {
        BleGattObserver hopped = hop(observer);
        return onSession(deviceId, hopped::onFailed, session -> session.gattOperationHandler().read(target, priority, hopped));
    }

    @NonNull
    public BleOperationHandle write(@NonNull BleDeviceId deviceId,
                                    @NonNull BleGattTarget target,
                                    @NonNull byte[] value,
                                    @NonNull BleWriteType writeType,
                                    @NonNull BleOperationPriority priority,
                                    @NonNull BleGattObserver observer) {
        BleGattObserver hopped = hop(observer);
        return onSession(deviceId, hopped::onFailed,
                session -> session.gattOperationHandler().write(target, value, writeType, priority, hopped));
    }

    @NonNull
    public BleOperationHandle writeSplit(@NonNull BleDeviceId deviceId,
                                         @NonNull BleGattTarget target,
                                         @NonNull byte[] value,
                                         @NonNull BleWriteType writeType,
                                         @Nullable BleDataSplitter splitter,
                                         @NonNull BleTransferProgressObserver observer) {
        return onSession(deviceId, observer::onFailed,
                session -> session.longWriteCoordinator().writeSplit(target, value, writeType, splitter, observer));
    }

    @NonNull
    public Result<Void> beginReliableWrite(@NonNull BleDeviceId deviceId) {
        if (publishedState(deviceId) != BleConnectionState.READY) {
            return Result.failure(missing(deviceId));
        }
        YoBleGattSession session = sessions.get(deviceId);
        if (session == null) {
            return Result.failure(missing(deviceId));
        }
        return session.reliableWriteCoordinator().beginFromAnyThread();
    }

    @NonNull
    public BleOperationHandle executeReliableWrite(@NonNull BleDeviceId deviceId,
                                                   @NonNull BleGattObserver observer) {
        BleGattObserver hopped = hop(observer);
        return onSession(deviceId, hopped::onFailed, session -> session.reliableWriteCoordinator().execute(hopped));
    }

    @NonNull
    public BleOperationHandle abortReliableWrite(@NonNull BleDeviceId deviceId,
                                                 @NonNull BleGattObserver observer) {
        BleGattObserver hopped = hop(observer);
        return onSession(deviceId, hopped::onFailed, session -> session.reliableWriteCoordinator().abort(hopped));
    }

    @NonNull
    public Result<Integer> getPendingOperationCount(@NonNull BleDeviceId deviceId) {
        YoBleGattSession session = sessions.get(deviceId);
        if (session == null) {
            return Result.failure(missing(deviceId));
        }
        return Result.success(session.publishedPendingCount());
    }

    @NonNull
    public Result<Integer> clearPendingOperations(@NonNull BleDeviceId deviceId) {
        YoBleGattSession session = sessions.get(deviceId);
        if (session == null) {
            return Result.failure(missing(deviceId));
        }
        int published = session.publishedPendingCount();
        runtime.state(() -> session.operationQueue().clearPending(YoBleErrorMapper.failure(
                BleErrorCode.OPERATION_CANCELLED, MODULE, "pending operations cleared", deviceId)));
        return Result.success(published);
    }

    @NonNull
    public BleOperationHandle subscribe(@NonNull BleDeviceId deviceId,
                                        @NonNull BleGattTarget target,
                                        @NonNull BleNotificationSetupMode setupMode,
                                        @NonNull BleNotificationObserver observer) {
        return onSession(deviceId, observer::onNotificationFailed, session -> {
            Runnable unsubscribe = session.notificationRouter().subscribe(target, setupMode, observer);
            return new BleOperationHandle("ble-sub-" + opSeq.incrementAndGet(), unsubscribe);
        });
    }

    @NonNull
    public BleOperationHandle setNotify(@NonNull BleDeviceId deviceId,
                                        @NonNull BleGattTarget target,
                                        boolean enabled,
                                        @NonNull BleGattObserver observer) {
        BleGattObserver hopped = hop(observer);
        return onSession(deviceId, hopped::onFailed,
                session -> session.notificationRouter().setNotify(target, enabled, hopped));
    }

    @NonNull
    public BleOperationHandle sendAndWait(@NonNull BleDeviceId deviceId,
                                          @NonNull BleRequestResponseConfig config,
                                          @NonNull BleValueObserver<byte[]> observer) {
        return onSession(deviceId, observer::onFailed,
                session -> session.requestResponseCoordinator().sendAndWait(config, observer));
    }

    public void onAdapterTurnedOff() {
        runtime.state(() -> {
            for (YoBleGattSession session : new ArrayList<>(sessions.values())) {
                session.onAdapterTurnedOff();
            }
        });
    }

    public void onBondStateChanged(@NonNull BleDeviceId deviceId, @NonNull BleBondState newState) {
        runtime.state(() -> {
            YoBleGattSession session = sessions.get(deviceId);
            if (session != null && !session.isEnded()) {
                session.onPlatformBondStateChanged(newState);
            }
        });
    }

    @Override
    public void release() {
        released = true;
        runtime.state(() -> {
            for (YoBleGattSession session : new ArrayList<>(sessions.values())) {
                session.forceClose();
            }
            sessions.clear();
            endedOrder.clear();
        });
    }

    private void openSession(@NonNull BleDeviceId deviceId,
                             @NonNull BleConnectionConfig config,
                             @NonNull BleConnectionObserver observer) {
        YoBleGattSession previous = sessions.get(deviceId);
        if (previous != null) {
            endedOrder.remove(deviceId);
            sessions.remove(deviceId, previous);
        }
        long lastClosed = lastClosedAtMillis.getOrDefault(deviceId, 0L);
        YoBleGattSession session = new YoBleGattSession(
                deviceId, runtime, bleConfig, config, capabilityInspector, connectionPlatform,
                bondController, snapshotStore, timeline, clockMillis, globalObservers, observer, lastClosed,
                lastClosedAtMillis::put);
        sessions.put(deviceId, session);
        session.whenEnded(() -> rememberEnded(deviceId, session));
        session.startConnect();
    }

    private void rememberEnded(@NonNull BleDeviceId deviceId, @NonNull YoBleGattSession session) {
        if (sessions.get(deviceId) != session) {
            return;
        }
        endedOrder.remove(deviceId);
        endedOrder.put(deviceId, Boolean.TRUE);
        while (endedOrder.size() > ENDED_SESSION_CAP) {
            BleDeviceId eldest = endedOrder.keySet().iterator().next();
            endedOrder.remove(eldest);
            YoBleGattSession old = sessions.get(eldest);
            if (old != null && old.isEnded()) {
                sessions.remove(eldest, old);
            }
        }
    }

    private void replay(@NonNull YoBleGattSession session, @NonNull BleConnectionObserver observer) {
        BleConnectionSnapshot snapshot = session.snapshot();
        BleGattDatabaseSnapshot database = session.currentDatabase();
        runtime.callback(() -> {
            observer.onConnectionStateChanged(snapshot);
            if (database != null) {
                observer.onServicesDiscovered(session.deviceId(), database);
            }
        });
    }

    @NonNull
    private BleOperationHandle onSession(@NonNull BleDeviceId deviceId,
                                         @NonNull Consumer<BleFailure> onError,
                                         @NonNull Function<YoBleGattSession, BleOperationHandle> action) {
        String opId = "ble-op-" + opSeq.incrementAndGet();
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AtomicReference<BleOperationHandle> inner = new AtomicReference<>();
        runtime.state(() -> {
            if (cancelled.get() || released) {
                if (released) {
                    runtime.callback(() -> onError.accept(releasedFailure(deviceId)));
                }
                return;
            }
            YoBleGattSession session = sessions.get(deviceId);
            if (session == null || session.isEnded() || !session.isLive() && !session.isReady()) {
                runtime.callback(() -> onError.accept(missing(deviceId)));
                return;
            }
            BleOperationHandle handle = action.apply(session);
            inner.set(handle);
            if (cancelled.get()) {
                handle.cancel();
            }
        });
        return new BleOperationHandle(opId, () -> {
            if (!cancelled.compareAndSet(false, true)) {
                return;
            }
            BleOperationHandle handle = inner.get();
            if (handle != null) {
                handle.cancel();
            }
        });
    }

    @NonNull
    private <T> BleValueObserver<T> hop(@NonNull BleValueObserver<T> observer) {
        return new BleValueObserver<T>() {
            @Override
            public void onSuccess(@NonNull T value) {
                runtime.callback(() -> observer.onSuccess(value));
            }

            @Override
            public void onFailed(@NonNull BleFailure failure) {
                runtime.callback(() -> observer.onFailed(failure));
            }
        };
    }

    @NonNull
    private BleGattObserver hop(@NonNull BleGattObserver observer) {
        return new BleGattObserver() {
            @Override
            public void onRead(@NonNull BleDeviceId deviceId, @NonNull BleGattTarget target, @NonNull byte[] value) {
                runtime.callback(() -> observer.onRead(deviceId, target, value));
            }

            @Override
            public void onWrite(@NonNull BleDeviceId deviceId, @NonNull BleGattTarget target) {
                runtime.callback(() -> observer.onWrite(deviceId, target));
            }

            @Override
            public void onDescriptorRead(@NonNull BleDeviceId deviceId, @NonNull BleGattTarget target, @NonNull byte[] value) {
                runtime.callback(() -> observer.onDescriptorRead(deviceId, target, value));
            }

            @Override
            public void onDescriptorWrite(@NonNull BleDeviceId deviceId, @NonNull BleGattTarget target) {
                runtime.callback(() -> observer.onDescriptorWrite(deviceId, target));
            }

            @Override
            public void onNotifyChanged(@NonNull BleDeviceId deviceId, @NonNull BleGattTarget target, boolean enabled) {
                runtime.callback(() -> observer.onNotifyChanged(deviceId, target, enabled));
            }

            @Override
            public void onCharacteristicChanged(@NonNull BleDeviceId deviceId, @NonNull BleGattTarget target, @NonNull byte[] value) {
                runtime.callback(() -> observer.onCharacteristicChanged(deviceId, target, value));
            }

            @Override
            public void onReliableWriteCompleted(@NonNull BleDeviceId deviceId, boolean committed) {
                runtime.callback(() -> observer.onReliableWriteCompleted(deviceId, committed));
            }

            @Override
            public void onFailed(@NonNull BleFailure failure) {
                runtime.callback(() -> observer.onFailed(failure));
            }
        };
    }

    @Nullable
    private BleFailure checkConnectPreflight(@NonNull BleDeviceId deviceId) {
        if (released || runtime.isReleased()) {
            return releasedFailure(deviceId);
        }
        if (!BleDeviceId.isValid(deviceId.value())) {
            return YoBleErrorMapper.failure(
                    BleErrorCode.DEVICE_INVALID, MODULE, "Invalid MAC address: " + deviceId, deviceId);
        }
        if (!capabilityInspector.isBleSupported()) {
            return YoBleErrorMapper.failure(
                    BleErrorCode.BLE_NOT_SUPPORTED, MODULE, "BLE not supported", deviceId);
        }
        if (!capabilityInspector.isAdapterEnabled()) {
            return YoBleErrorMapper.failure(
                    BleErrorCode.BLUETOOTH_DISABLED, MODULE, "Bluetooth adapter disabled", deviceId);
        }
        if (!capabilityInspector.hasConnectPermission()) {
            return YoBleErrorMapper.failure(
                    BleErrorCode.PERMISSION_CONNECT_DENIED, MODULE, "Missing BLUETOOTH_CONNECT permission", deviceId);
        }
        return null;
    }

    @NonNull
    private BleConnectionState publishedState(@NonNull BleDeviceId deviceId) {
        BleDeviceSnapshot stored = snapshotStore.get(deviceId);
        if (stored == null || stored.connectionSnapshot() == null) {
            return BleConnectionState.IDLE;
        }
        return stored.connectionSnapshot().state();
    }

    private static boolean isLinkUp(@NonNull BleConnectionState state) {
        return state == BleConnectionState.CONNECTED
                || state == BleConnectionState.BONDING_WAIT
                || state == BleConnectionState.DISCOVERING_SERVICES
                || state == BleConnectionState.INITIALIZING
                || state == BleConnectionState.READY;
    }

    @NonNull
    private BleFailure missing(@NonNull BleDeviceId deviceId) {
        if (released) {
            return releasedFailure(deviceId);
        }
        return YoBleErrorMapper.failure(
                BleErrorCode.CONNECTION_NOT_READY, MODULE, "No active BLE session for " + deviceId, deviceId);
    }

    @NonNull
    private BleFailure releasedFailure(@Nullable BleDeviceId deviceId) {
        return YoBleErrorMapper.failure(BleErrorCode.RELEASED, MODULE, "Client released", deviceId);
    }
}
