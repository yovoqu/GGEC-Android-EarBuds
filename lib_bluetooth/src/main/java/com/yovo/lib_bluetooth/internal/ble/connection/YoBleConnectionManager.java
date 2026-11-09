package com.yovo.lib_bluetooth.internal.ble.connection;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.ble.device.YoBleCapabilityInspector;
import com.yovo.lib_bluetooth.internal.ble.device.YoBleDeviceSnapshotStore;
import com.yovo.lib_bluetooth.internal.ble.diagnostics.YoBleTimelineRecorder;
import com.yovo.lib_bluetooth.internal.ble.error.YoBleErrorMapper;
import com.yovo.lib_bluetooth.internal.ble.gatt.YoBleGattTransport;
import com.yovo.lib_bluetooth.internal.ble.pairing.YoBleBondController;
import com.yovo.lib_bluetooth.internal.ble.runtime.YoBleRuntime;
import com.yovo.lib_bluetooth.internal.common.lifecycle.YoReleasable;
import com.yovo.lib_bluetooth.model.ble.BleBondState;
import com.yovo.lib_bluetooth.model.ble.BleConfig;
import com.yovo.lib_bluetooth.model.ble.BleConnectionConfig;
import com.yovo.lib_bluetooth.model.ble.BleConnectionObserver;
import com.yovo.lib_bluetooth.model.ble.BleConnectionPriority;
import com.yovo.lib_bluetooth.model.ble.BleConnectionSnapshot;
import com.yovo.lib_bluetooth.model.ble.BleDataSplitter;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

/**
 * 多设备并发 BLE GATT 会话管理器：严格隔离每台设备的会话/队列/路由/链路状态，
 * 负责连接前检、重复连接复用/替换、重连、全局 BT OFF 清理与释放。
 */
public final class YoBleConnectionManager implements YoReleasable {

    private static final String MODULE = "ble-connection-mgr";

    private final YoBleRuntime runtime;
    private final BleConfig bleConfig;
    private final YoBleCapabilityInspector capabilityInspector;
    private final YoBleConnectionPlatform connectionPlatform;
    private final YoBleBondController bondController;
    private final YoBleDeviceSnapshotStore snapshotStore;
    private final YoBleTimelineRecorder timeline;
    private final LongSupplier clockMillis;

    private final Map<BleDeviceId, YoBleGattSession> sessions = new LinkedHashMap<>();
    private final Map<BleDeviceId, BleConnectionConfig> lastConnectionConfigs = new LinkedHashMap<>();
    private final Map<BleDeviceId, Long> lastClosedAtMillis = new LinkedHashMap<>();
    private final CopyOnWriteArrayList<BleConnectionObserver> globalObservers = new CopyOnWriteArrayList<>();
    private final AtomicLong opSeq = new AtomicLong();
    private volatile boolean released = false;

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
                timeline.record(MODULE, "connect preflight rejected " + deviceId
                        + ": " + preflight.errorCode());
                runtime.callback(() -> observer.onConnectionFailed(preflight));
                return;
            }
            synchronized (lastConnectionConfigs) {
                lastConnectionConfigs.put(deviceId, config);
            }

            YoBleGattSession existing;
            synchronized (sessions) {
                existing = sessions.get(deviceId);
            }

            if (existing != null && existing.isReady()) {
                existing.attachObserver(observer);
                BleConnectionSnapshot readySnap = existing.snapshot();
                BleGattDatabaseSnapshot db = existing.currentDatabase();
                runtime.callback(() -> observer.onConnectionStateChanged(readySnap));
                if (db != null) {
                    runtime.callback(() -> observer.onServicesDiscovered(deviceId, db));
                }
                return;
            }

            if (existing != null) {
                existing.releaseSession();
                synchronized (sessions) {
                    sessions.remove(deviceId, existing);
                }
            }

            long lastClosed;
            synchronized (lastClosedAtMillis) {
                Long ts = lastClosedAtMillis.get(deviceId);
                lastClosed = ts == null ? 0L : ts;
            }

            YoBleGattSession session = new YoBleGattSession(
                    deviceId,
                    runtime,
                    bleConfig,
                    config,
                    capabilityInspector,
                    connectionPlatform,
                    bondController,
                    snapshotStore,
                    timeline,
                    clockMillis,
                    globalObservers,
                    observer,
                    lastClosed,
                    (id, ts) -> {
                        synchronized (lastClosedAtMillis) {
                            lastClosedAtMillis.put(id, ts);
                        }
                    });
            synchronized (sessions) {
                sessions.put(deviceId, session);
            }
            session.startConnect();
        });

        return new BleOperationHandle(opId, () -> {
            if (cancelled.compareAndSet(false, true)) {
                disconnect(deviceId);
            }
        });
    }

    @NonNull
    public Result<Void> reconnect(@NonNull BleDeviceId deviceId) {
        if (released) {
            return Result.failure(YoBleErrorMapper.failure(
                    BleErrorCode.RELEASED, MODULE, "Client released", deviceId));
        }
        BleFailure preflight = checkConnectPreflight(deviceId);
        if (preflight != null) {
            return Result.failure(preflight);
        }
        YoBleGattSession existing;
        synchronized (sessions) {
            existing = sessions.get(deviceId);
        }
        BleConnectionConfig historicalConfig = null;
        if (existing == null) {
            synchronized (lastConnectionConfigs) {
                historicalConfig = lastConnectionConfigs.get(deviceId);
            }
        }
        if (existing == null && historicalConfig == null) {
            return Result.failure(YoBleErrorMapper.failure(
                    BleErrorCode.DEVICE_INVALID, MODULE,
                    "No existing session config to reconnect for " + deviceId, deviceId));
        }
        runtime.state(() -> {
            if (released) {
                return;
            }
            YoBleGattSession current;
            synchronized (sessions) {
                current = sessions.get(deviceId);
            }
            BleConnectionConfig cfg;
            if (current != null) {
                cfg = current.connectionConfig();
                current.releaseSession();
                synchronized (sessions) {
                    sessions.remove(deviceId, current);
                }
            } else {
                synchronized (lastConnectionConfigs) {
                    cfg = lastConnectionConfigs.get(deviceId);
                }
            }
            if (cfg == null) {
                return;
            }
            long lastClosed;
            synchronized (lastClosedAtMillis) {
                Long ts = lastClosedAtMillis.get(deviceId);
                lastClosed = ts == null ? 0L : ts;
            }
            YoBleGattSession fresh = new YoBleGattSession(
                    deviceId, runtime, bleConfig, cfg, capabilityInspector,
                    connectionPlatform, bondController, snapshotStore, timeline,
                    clockMillis, globalObservers, new BleConnectionObserver() {
                    },
                    lastClosed,
                    (id, ts) -> {
                        synchronized (lastClosedAtMillis) {
                            lastClosedAtMillis.put(id, ts);
                        }
                    });
            synchronized (sessions) {
                sessions.put(deviceId, fresh);
            }
            fresh.startConnect();
        });
        return Result.success(null);
    }

    @NonNull
    public Result<Void> disconnect(@NonNull BleDeviceId deviceId) {
        if (released) {
            return Result.failure(YoBleErrorMapper.failure(
                    BleErrorCode.RELEASED, MODULE, "Client released", deviceId));
        }
        runtime.state(() -> {
            YoBleGattSession session;
            synchronized (sessions) {
                session = sessions.remove(deviceId);
            }
            if (session != null) {
                session.userDisconnect();
            }
        });
        return Result.success(null);
    }

    @NonNull
    public Result<Void> disconnectAll() {
        if (released) {
            return Result.failure(YoBleErrorMapper.failure(
                    BleErrorCode.RELEASED, MODULE, "Client released"));
        }
        runtime.state(() -> {
            List<YoBleGattSession> copy;
            synchronized (sessions) {
                copy = new ArrayList<>(sessions.values());
                sessions.clear();
            }
            for (YoBleGattSession s : copy) {
                s.userDisconnect();
            }
        });
        return Result.success(null);
    }

    @NonNull
    public Result<BleConnectionSnapshot> getConnectionSnapshot(@NonNull BleDeviceId deviceId) {
        if (released) {
            return Result.failure(YoBleErrorMapper.failure(
                    BleErrorCode.RELEASED, MODULE, "Client released", deviceId));
        }
        if (!BleDeviceId.isValid(deviceId.value())) {
            return Result.failure(YoBleErrorMapper.failure(
                    BleErrorCode.DEVICE_INVALID, MODULE, "Invalid MAC address: " + deviceId, deviceId));
        }
        YoBleGattSession session;
        synchronized (sessions) {
            session = sessions.get(deviceId);
        }
        if (session == null) {
            return Result.failure(YoBleErrorMapper.failure(
                    BleErrorCode.DEVICE_INVALID, MODULE,
                    "BLE session not found for " + deviceId, deviceId));
        }
        return Result.success(session.snapshot());
    }

    @NonNull
    public Result<List<BleConnectionSnapshot>> getConnectedDevices() {
        if (released) {
            return Result.failure(YoBleErrorMapper.failure(
                    BleErrorCode.RELEASED, MODULE, "Client released"));
        }
        List<YoBleGattSession> copy;
        synchronized (sessions) {
            copy = new ArrayList<>(sessions.values());
        }
        List<BleConnectionSnapshot> list = new ArrayList<>();
        for (YoBleGattSession session : copy) {
            BleConnectionSnapshot snap = session.snapshot();
            if (snap.isConnected()) {
                list.add(snap);
            }
        }
        return Result.success(Collections.unmodifiableList(list));
    }

    @NonNull
    public List<BleConnectionSnapshot> exportActiveSnapshots() {
        List<YoBleGattSession> copy;
        synchronized (sessions) {
            copy = new ArrayList<>(sessions.values());
        }
        List<BleConnectionSnapshot> list = new ArrayList<>(copy.size());
        for (YoBleGattSession s : copy) {
            list.add(s.snapshot());
        }
        return list;
    }

    public int pendingCountAtSnapshotIndex(int index) {
        List<YoBleGattSession> copy;
        synchronized (sessions) {
            copy = new ArrayList<>(sessions.values());
        }
        if (index < 0 || index >= copy.size()) {
            return 0;
        }
        return copy.get(index).operationQueue().pendingCount();
    }

    @NonNull
    public BleOperationHandle observeConnectionState(@NonNull BleConnectionObserver observer) {
        String opId = "ble-conn-obs-" + opSeq.incrementAndGet();
        globalObservers.addIfAbsent(observer);
        return new BleOperationHandle(opId, () -> globalObservers.remove(observer));
    }

    // ── 链路调优、发现与 GATT 操作代理 ──────────────────────────────

    @NonNull
    public BleOperationHandle requestMtu(@NonNull BleDeviceId deviceId,
                                         int mtu,
                                         @NonNull BleValueObserver<Integer> observer) {
        return dispatchSessionHandle("mtu", deviceId,
                f -> observer.onFailed(f),
                session -> session.linkTuner().requestMtu(mtu, observer));
    }

    @NonNull
    public Result<Integer> overrideMtu(@NonNull BleDeviceId deviceId, int mtu) {
        Result<YoBleGattSession> r = requireSessionSync(deviceId);
        if (r.isFailure()) {
            return Result.failure(r.getFailure());
        }
        return r.getValue().linkTuner().overrideMtu(mtu);
    }

    @NonNull
    public BleOperationHandle requestConnectionPriority(@NonNull BleDeviceId deviceId,
                                                        @NonNull BleConnectionPriority priority,
                                                        @NonNull BleValueObserver<Void> observer) {
        return dispatchSessionHandle("conn-prio", deviceId,
                observer::onFailed,
                session -> session.linkTuner().requestConnectionPriority(priority, observer));
    }

    @NonNull
    public BleOperationHandle setPreferredPhy(@NonNull BleDeviceId deviceId,
                                              @NonNull BlePhy txPhy,
                                              @NonNull BlePhy rxPhy,
                                              @NonNull BlePhyOptions phyOptions,
                                              @NonNull BleValueObserver<BlePhyConfig> observer) {
        return dispatchSessionHandle("set-phy", deviceId,
                observer::onFailed,
                session -> session.linkTuner().setPreferredPhy(txPhy, rxPhy, phyOptions, observer));
    }

    @NonNull
    public BleOperationHandle readPhy(@NonNull BleDeviceId deviceId,
                                      @NonNull BleValueObserver<BlePhyConfig> observer) {
        return dispatchSessionHandle("read-phy", deviceId,
                observer::onFailed,
                session -> session.linkTuner().readPhy(observer));
    }

    @NonNull
    public BleOperationHandle readRemoteRssi(@NonNull BleDeviceId deviceId,
                                             @NonNull BleValueObserver<Integer> observer) {
        return dispatchSessionHandle("rssi", deviceId,
                observer::onFailed,
                session -> session.linkTuner().readRemoteRssi(observer));
    }

    @NonNull
    public BleOperationHandle discoverServices(@NonNull BleDeviceId deviceId,
                                               boolean forceRefresh,
                                               @NonNull BleValueObserver<BleGattDatabaseSnapshot> observer) {
        return dispatchSessionHandle("discover", deviceId,
                observer::onFailed,
                session -> {
                    if (forceRefresh && !bleConfig.allowHiddenApiCacheRefresh()) {
                        BleFailure blocked = YoBleErrorMapper.failure(
                                BleErrorCode.HIDDEN_API_BLOCKED, MODULE,
                                "forceRefresh requires BleConfig.allowHiddenApiCacheRefresh(true)", deviceId);
                        runtime.callback(() -> observer.onFailed(blocked));
                        return BleOperationHandle.completed("ble-discover-blocked-" + opSeq.incrementAndGet());
                    }
                    return session.serviceDiscoveryManager().discoverServices(
                            forceRefresh,
                            session.connectionConfig().autoHandleServiceChanged(),
                            observer);
                });
    }

    @NonNull
    public Result<BleGattDatabaseSnapshot> getServices(@NonNull BleDeviceId deviceId) {
        Result<YoBleGattSession> r = requireSessionSync(deviceId);
        if (r.isFailure()) {
            return Result.failure(r.getFailure());
        }
        BleGattDatabaseSnapshot db = r.getValue().currentDatabase();
        if (db == null) {
            return Result.failure(YoBleErrorMapper.failure(
                    BleErrorCode.CONNECTION_NOT_READY, MODULE,
                    "Services have not been discovered yet", deviceId));
        }
        return Result.success(db);
    }

    @NonNull
    public Result<Void> refreshDeviceCache(@NonNull BleDeviceId deviceId) {
        if (!bleConfig.allowHiddenApiCacheRefresh()) {
            return Result.failure(YoBleErrorMapper.failure(
                    BleErrorCode.HIDDEN_API_BLOCKED, MODULE,
                    "refreshDeviceCache requires BleConfig.allowHiddenApiCacheRefresh(true)",
                    deviceId));
        }
        Result<YoBleGattSession> r = requireSessionSync(deviceId);
        if (r.isFailure()) {
            return Result.failure(r.getFailure());
        }
        YoBleGattTransport transport = r.getValue().transport();
        if (transport == null) {
            return Result.failure(YoBleErrorMapper.failure(
                    BleErrorCode.CONNECTION_NOT_READY, MODULE,
                    "GATT transport unavailable", deviceId));
        }
        boolean ok = transport.refreshDeviceCache();
        timeline.record(MODULE, "refreshDeviceCache(" + deviceId + ")=" + ok);
        return ok ? Result.success(null) : Result.failure(YoBleErrorMapper.failure(
                BleErrorCode.HIDDEN_API_FAILED, MODULE,
                "BluetoothGatt.refresh() returned false", deviceId));
    }

    @NonNull
    public BleOperationHandle read(@NonNull BleDeviceId deviceId,
                                   @NonNull BleGattTarget target,
                                   @NonNull BleOperationPriority priority,
                                   @NonNull BleGattObserver observer) {
        return dispatchSessionHandle("read", deviceId,
                observer::onFailed,
                session -> session.gattOperationHandler().read(target, priority, observer));
    }

    @NonNull
    public BleOperationHandle write(@NonNull BleDeviceId deviceId,
                                    @NonNull BleGattTarget target,
                                    @NonNull byte[] value,
                                    @NonNull BleWriteType writeType,
                                    @NonNull BleOperationPriority priority,
                                    @NonNull BleGattObserver observer) {
        byte[] copy = value.clone();
        return dispatchSessionHandle("write", deviceId,
                observer::onFailed,
                session -> session.gattOperationHandler().write(
                        target, copy, writeType, priority, observer));
    }

    @NonNull
    public BleOperationHandle writeSplit(@NonNull BleDeviceId deviceId,
                                         @NonNull BleGattTarget target,
                                         @NonNull byte[] value,
                                         @NonNull BleWriteType writeType,
                                         @Nullable BleDataSplitter customSplitter,
                                         @NonNull BleTransferProgressObserver observer) {
        byte[] copy = value.clone();
        return dispatchSessionHandle("write-split", deviceId,
                observer::onFailed,
                session -> session.longWriteCoordinator().writeSplit(
                        target, copy, writeType, customSplitter, observer));
    }

    @NonNull
    public Result<Void> beginReliableWrite(@NonNull BleDeviceId deviceId) {
        Result<YoBleGattSession> r = requireSessionSync(deviceId);
        if (r.isFailure()) {
            return Result.failure(r.getFailure());
        }
        return r.getValue().reliableWriteCoordinator().beginReliableWrite();
    }

    @NonNull
    public BleOperationHandle executeReliableWrite(@NonNull BleDeviceId deviceId,
                                                   @NonNull BleGattObserver observer) {
        return dispatchSessionHandle("reliable-exec", deviceId,
                observer::onFailed,
                session -> session.reliableWriteCoordinator().executeReliableWrite(observer));
    }

    @NonNull
    public BleOperationHandle abortReliableWrite(@NonNull BleDeviceId deviceId,
                                                 @NonNull BleGattObserver observer) {
        return dispatchSessionHandle("reliable-abort", deviceId,
                observer::onFailed,
                session -> session.reliableWriteCoordinator().abortReliableWrite(observer));
    }

    @NonNull
    public Result<Integer> getPendingOperationCount(@NonNull BleDeviceId deviceId) {
        Result<YoBleGattSession> r = requireSessionSync(deviceId);
        if (r.isFailure()) {
            return Result.failure(r.getFailure());
        }
        return Result.success(r.getValue().operationQueue().pendingCount());
    }

    @NonNull
    public Result<Integer> clearPendingOperations(@NonNull BleDeviceId deviceId) {
        Result<YoBleGattSession> r = requireSessionSync(deviceId);
        if (r.isFailure()) {
            return Result.failure(r.getFailure());
        }
        int cleared = r.getValue().operationQueue().clearAll(YoBleErrorMapper.failure(
                BleErrorCode.OPERATION_CANCELLED, MODULE,
                "Cleared pending GATT operations by caller", deviceId));
        return Result.success(cleared);
    }

    @NonNull
    public BleOperationHandle subscribe(@NonNull BleDeviceId deviceId,
                                        @NonNull BleGattTarget target,
                                        @NonNull BleNotificationSetupMode setupMode,
                                        @NonNull BleNotificationObserver observer) {
        String opId = "ble-sub-" + opSeq.incrementAndGet();
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AtomicReference<Runnable> unsubRef = new AtomicReference<>();

        runtime.state(() -> {
            if (cancelled.get()) {
                return;
            }
            YoBleGattSession session = activeSessionLocked(deviceId);
            if (session == null) {
                BleFailure f = sessionMissingFailure(deviceId);
                runtime.callback(() -> observer.onNotificationFailed(f));
                return;
            }
            Runnable unsub = session.notificationRouter().subscribe(target, setupMode, observer);
            unsubRef.set(unsub);
            if (cancelled.get()) {
                unsub.run();
            }
        });

        return new BleOperationHandle(opId, () -> {
            if (cancelled.compareAndSet(false, true)) {
                runtime.state(() -> {
                    Runnable unsub = unsubRef.get();
                    if (unsub != null) {
                        unsub.run();
                    }
                });
            }
        });
    }

    @NonNull
    public BleOperationHandle setNotify(@NonNull BleDeviceId deviceId,
                                        @NonNull BleGattTarget target,
                                        boolean enabled,
                                        @NonNull BleGattObserver observer) {
        return dispatchSessionHandle("notify", deviceId,
                observer::onFailed,
                session -> session.notificationRouter().setNotifyCompat(target, enabled, observer));
    }

    @NonNull
    public BleOperationHandle sendAndWait(@NonNull BleDeviceId deviceId,
                                          @NonNull BleRequestResponseConfig config,
                                          @NonNull BleValueObserver<byte[]> observer) {
        return dispatchSessionHandle("req-resp", deviceId,
                observer::onFailed,
                session -> session.requestResponseCoordinator().sendAndWait(config, observer));
    }

    // ── 系统广播转发与释放 ──────────────────────────────────────────

    public void onAdapterTurnedOff() {
        runtime.state(() -> {
            List<YoBleGattSession> copy;
            synchronized (sessions) {
                copy = new ArrayList<>(sessions.values());
            }
            for (YoBleGattSession s : copy) {
                s.onAdapterTurnedOff();
            }
        });
    }

    public void onBondStateChanged(@NonNull BleDeviceId deviceId, @NonNull BleBondState newState) {
        runtime.state(() -> {
            YoBleGattSession s = activeSessionLocked(deviceId);
            if (s != null) {
                s.onPlatformBondStateChanged(newState);
            }
        });
    }

    @Override
    public void release() {
        released = true;
        runtime.state(() -> {
            List<YoBleGattSession> copy;
            synchronized (sessions) {
                copy = new ArrayList<>(sessions.values());
                sessions.clear();
            }
            for (YoBleGattSession s : copy) {
                s.releaseSession();
            }
            synchronized (lastConnectionConfigs) {
                lastConnectionConfigs.clear();
            }
            globalObservers.clear();
        });
    }

    // ── 辅助方法 ────────────────────────────────────────────────────

    private interface SessionHandleAction {
        @NonNull
        BleOperationHandle execute(@NonNull YoBleGattSession session);
    }

    @NonNull
    private BleOperationHandle dispatchSessionHandle(@NonNull String tag,
                                                     @NonNull BleDeviceId deviceId,
                                                     @NonNull java.util.function.Consumer<BleFailure> errorSink,
                                                     @NonNull SessionHandleAction action) {
        String opId = "ble-" + tag + "-" + opSeq.incrementAndGet();
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AtomicReference<BleOperationHandle> inner = new AtomicReference<>();

        runtime.state(() -> {
            if (cancelled.get()) {
                BleFailure cancelErr = YoBleErrorMapper.failure(
                        BleErrorCode.OPERATION_CANCELLED, MODULE,
                        "Operation cancelled before execution: " + opId, deviceId);
                runtime.callback(() -> errorSink.accept(cancelErr));
                return;
            }
            YoBleGattSession session = activeSessionLocked(deviceId);
            if (session == null) {
                BleFailure f = sessionMissingFailure(deviceId);
                runtime.callback(() -> errorSink.accept(f));
                return;
            }
            BleOperationHandle h = action.execute(session);
            inner.set(h);
            if (cancelled.get()) {
                h.cancel();
            }
        });

        return new BleOperationHandle(opId, () -> {
            if (cancelled.compareAndSet(false, true)) {
                BleOperationHandle h = inner.get();
                if (h != null) {
                    h.cancel();
                }
            }
        });
    }

    @NonNull
    private Result<YoBleGattSession> requireSessionSync(@NonNull BleDeviceId deviceId) {
        if (released) {
            return Result.failure(YoBleErrorMapper.failure(
                    BleErrorCode.RELEASED, MODULE, "Client released", deviceId));
        }
        if (!BleDeviceId.isValid(deviceId.value())) {
            return Result.failure(YoBleErrorMapper.failure(
                    BleErrorCode.DEVICE_INVALID, MODULE, "Invalid MAC address: " + deviceId, deviceId));
        }
        YoBleGattSession session = activeSessionLocked(deviceId);
        if (session == null) {
            return Result.failure(sessionMissingFailure(deviceId));
        }
        return Result.success(session);
    }

    @Nullable
    private YoBleGattSession activeSessionLocked(@NonNull BleDeviceId deviceId) {
        synchronized (sessions) {
            return sessions.get(deviceId);
        }
    }

    @NonNull
    private BleFailure sessionMissingFailure(@NonNull BleDeviceId deviceId) {
        if (released) {
            return YoBleErrorMapper.failure(BleErrorCode.RELEASED, MODULE, "Client released", deviceId);
        }
        if (!BleDeviceId.isValid(deviceId.value())) {
            return YoBleErrorMapper.failure(
                    BleErrorCode.DEVICE_INVALID, MODULE, "Invalid MAC address: " + deviceId, deviceId);
        }
        return YoBleErrorMapper.failure(
                BleErrorCode.CONNECTION_NOT_READY, MODULE,
                "No active BLE session for " + deviceId, deviceId);
    }

    @Nullable
    private BleFailure checkConnectPreflight(@NonNull BleDeviceId deviceId) {
        if (released || runtime.isReleased()) {
            return YoBleErrorMapper.failure(
                    BleErrorCode.RELEASED, MODULE, "Client released", deviceId);
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
                    BleErrorCode.PERMISSION_CONNECT_DENIED, MODULE,
                    "Missing BLUETOOTH_CONNECT permission", deviceId);
        }
        return null;
    }
}
