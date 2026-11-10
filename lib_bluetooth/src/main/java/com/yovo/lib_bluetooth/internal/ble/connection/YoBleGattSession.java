package com.yovo.lib_bluetooth.internal.ble.connection;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.ble.device.YoBleCapabilityInspector;
import com.yovo.lib_bluetooth.internal.ble.device.YoBleDeviceSnapshotStore;
import com.yovo.lib_bluetooth.internal.ble.diagnostics.YoBleTimelineRecorder;
import com.yovo.lib_bluetooth.internal.ble.error.YoBleErrorMapper;
import com.yovo.lib_bluetooth.internal.ble.error.YoBleGattStatus;
import com.yovo.lib_bluetooth.internal.ble.error.YoBleHciStatus;
import com.yovo.lib_bluetooth.internal.ble.gatt.YoBleGattOperationHandler;
import com.yovo.lib_bluetooth.internal.ble.gatt.YoBleGattOperationQueue;
import com.yovo.lib_bluetooth.internal.ble.gatt.YoBleGattTransport;
import com.yovo.lib_bluetooth.internal.ble.gatt.YoBleLinkTuner;
import com.yovo.lib_bluetooth.internal.ble.gatt.YoBleNotificationRouter;
import com.yovo.lib_bluetooth.internal.ble.gatt.YoBleReliableWriteCoordinator;
import com.yovo.lib_bluetooth.internal.ble.gatt.YoBleServiceDiscoveryManager;
import com.yovo.lib_bluetooth.internal.ble.gatt.YoBleSessionContext;
import com.yovo.lib_bluetooth.internal.ble.pairing.YoBleBondController;
import com.yovo.lib_bluetooth.internal.ble.runtime.YoBleRuntime;
import com.yovo.lib_bluetooth.internal.ble.transfer.YoBleLongWriteCoordinator;
import com.yovo.lib_bluetooth.internal.ble.transfer.YoBleRequestResponseCoordinator;
import com.yovo.lib_bluetooth.internal.common.concurrency.YoTimeoutHandle;
import com.yovo.lib_bluetooth.model.ble.BleBondPolicy;
import com.yovo.lib_bluetooth.model.ble.BleBondState;
import com.yovo.lib_bluetooth.model.ble.BleConfig;
import com.yovo.lib_bluetooth.model.ble.BleConnectionConfig;
import com.yovo.lib_bluetooth.model.ble.BleConnectionObserver;
import com.yovo.lib_bluetooth.model.ble.BleConnectionParameters;
import com.yovo.lib_bluetooth.model.ble.BleConnectionPriority;
import com.yovo.lib_bluetooth.model.ble.BleConnectionSnapshot;
import com.yovo.lib_bluetooth.model.ble.BleConnectionState;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleDeviceSnapshot;
import com.yovo.lib_bluetooth.model.ble.BleErrorCode;
import com.yovo.lib_bluetooth.model.ble.BleFailure;
import com.yovo.lib_bluetooth.model.ble.BleGattDatabaseSnapshot;
import com.yovo.lib_bluetooth.model.ble.BleGattTarget;
import com.yovo.lib_bluetooth.model.ble.BlePhyConfig;
import com.yovo.lib_bluetooth.model.ble.BleValueObserver;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * 单设备连接会话。状态只在 state 线程变化。
 * autoConnect 关闭时丢链即结束；打开时首次直连，READY 之后的丢链改为无库超时的 autoConnect。
 */
public final class YoBleGattSession implements YoBleSessionContext {

    private static final String MODULE = "ble-session";
    private static final long DISCONNECT_CLOSE_TIMEOUT_MS = 2_000L;

    @NonNull
    private final BleDeviceId deviceId;
    @NonNull
    private final YoBleRuntime runtime;
    @NonNull
    private final BleConfig bleConfig;
    @NonNull
    private final BleConnectionConfig connectionConfig;
    @NonNull
    private final YoBleCapabilityInspector capabilityInspector;
    @NonNull
    private final YoBleConnectionPlatform connectionPlatform;
    @NonNull
    private final YoBleBondController bondController;
    @NonNull
    private final YoBleDeviceSnapshotStore snapshotStore;
    @NonNull
    private final YoBleTimelineRecorder timeline;
    @NonNull
    private final LongSupplier clockMillis;
    @NonNull
    private final CopyOnWriteArrayList<BleConnectionObserver> globalObservers;
    @NonNull
    private final CopyOnWriteArrayList<BleConnectionObserver> sessionObservers = new CopyOnWriteArrayList<>();
    @NonNull
    private final BiConsumer<BleDeviceId, Long> closeTimestampSink;
    @NonNull
    private final YoBleConnectionStateMachine stateMachine = new YoBleConnectionStateMachine();
    @NonNull
    private final YoBleGattOperationQueue operationQueue;
    @NonNull
    private final YoBleNotificationRouter notificationRouter;
    @NonNull
    private final YoBleServiceDiscoveryManager discoveryManager;
    @NonNull
    private final YoBleReliableWriteCoordinator reliableWrite;
    @NonNull
    private final YoBleGattOperationHandler gattHandler;
    @NonNull
    private final YoBleLinkTuner linkTuner;
    @NonNull
    private final YoBleLongWriteCoordinator longWrite;
    @NonNull
    private final YoBleRequestResponseCoordinator requestResponse;
    @NonNull
    private final List<Runnable> endedWaiters = new ArrayList<>();

    private long generation;
    private int retryAttempt;
    private int currentMtu = BleConnectionSnapshot.DEFAULT_ATT_MTU;
    @Nullable
    private Integer overriddenMtu;
    @NonNull
    private BlePhyConfig currentPhy = BlePhyConfig.le1m();
    @Nullable
    private BleConnectionParameters connectionParameters;
    @NonNull
    private BleBondState bondState;
    @Nullable
    private Integer lastRssi;
    @Nullable
    private BleGattDatabaseSnapshot currentDatabase;
    @Nullable
    private BleFailure lastFailure;
    private long lastCloseAtMillis;
    private boolean userDisconnected;
    private boolean backgroundAutoConnect;
    private boolean ended;
    @Nullable
    private YoBleConnectionPlatform.BleGattHandle handle;
    @Nullable
    private YoTimeoutHandle connectTimeout;
    @Nullable
    private YoTimeoutHandle retryHandle;
    @Nullable
    private YoTimeoutHandle closeTimeout;

    public YoBleGattSession(@NonNull BleDeviceId deviceId,
                            @NonNull YoBleRuntime runtime,
                            @NonNull BleConfig bleConfig,
                            @NonNull BleConnectionConfig connectionConfig,
                            @NonNull YoBleCapabilityInspector capabilityInspector,
                            @NonNull YoBleConnectionPlatform connectionPlatform,
                            @NonNull YoBleBondController bondController,
                            @NonNull YoBleDeviceSnapshotStore snapshotStore,
                            @NonNull YoBleTimelineRecorder timeline,
                            @NonNull LongSupplier clockMillis,
                            @NonNull CopyOnWriteArrayList<BleConnectionObserver> globalObservers,
                            @NonNull BleConnectionObserver initialObserver,
                            long lastCloseAtMillis,
                            @NonNull BiConsumer<BleDeviceId, Long> closeTimestampSink) {
        this.deviceId = deviceId;
        this.runtime = runtime;
        this.bleConfig = bleConfig;
        this.connectionConfig = connectionConfig;
        this.capabilityInspector = capabilityInspector;
        this.connectionPlatform = connectionPlatform;
        this.bondController = bondController;
        this.snapshotStore = snapshotStore;
        this.timeline = timeline;
        this.clockMillis = clockMillis;
        this.globalObservers = globalObservers;
        this.closeTimestampSink = closeTimestampSink;
        this.lastCloseAtMillis = lastCloseAtMillis;
        this.bondState = bondController.getBondState(deviceId);
        this.sessionObservers.add(initialObserver);
        this.operationQueue = new YoBleGattOperationQueue(deviceId, runtime, bleConfig.gattOperationQueueCapacity());
        this.notificationRouter = new YoBleNotificationRouter(this);
        this.discoveryManager = new YoBleServiceDiscoveryManager(this);
        this.reliableWrite = new YoBleReliableWriteCoordinator(this);
        this.gattHandler = new YoBleGattOperationHandler(this, reliableWrite);
        this.linkTuner = new YoBleLinkTuner(this);
        this.longWrite = new YoBleLongWriteCoordinator(this, gattHandler);
        this.requestResponse = new YoBleRequestResponseCoordinator(this, gattHandler);
    }

    public void attachObserver(@NonNull BleConnectionObserver observer) {
        sessionObservers.addIfAbsent(observer);
    }

    public void detachObserver(@NonNull BleConnectionObserver observer) {
        sessionObservers.remove(observer);
    }

    public int observerCount() {
        return sessionObservers.size();
    }

    public boolean isLive() {
        return !ended && (stateMachine.isConnected()
                || stateMachine.state() == BleConnectionState.CONNECTING);
    }

    public boolean isDisconnecting() {
        return stateMachine.state() == BleConnectionState.DISCONNECTING;
    }

    public boolean isEnded() {
        return ended;
    }

    public boolean isReady() {
        return !ended && stateMachine.isReady();
    }

    public void whenEnded(@NonNull Runnable action) {
        if (ended) {
            action.run();
            return;
        }
        endedWaiters.add(action);
    }

    @NonNull
    public BleConnectionConfig connectionConfig() {
        return connectionConfig;
    }

    public void startConnect() {
        runtime.checkStateThread();
        ended = false;
        userDisconnected = false;
        backgroundAutoConnect = false;
        retryAttempt = 0;
        lastFailure = null;
        transition(BleConnectionState.CONNECTING, "start");
        publish();
        long wait = remainingCloseGap();
        runtime.stateDelayed(this::launchConnectAttempt, wait);
    }

    public void userDisconnect() {
        runtime.checkStateThread();
        userDisconnected = true;
        cancelTimers();
        if (stateMachine.isConnected()) {
            gracefulDisconnect();
            return;
        }
        finishTerminal(true, YoBleErrorMapper.failure(
                BleErrorCode.LOCAL_TERMINATED, MODULE, "disconnected by user", deviceId), false);
    }

    public void forceClose() {
        runtime.checkStateThread();
        userDisconnected = true;
        finishTerminal(true, YoBleErrorMapper.failure(
                BleErrorCode.RELEASED, MODULE, "session released", deviceId), false);
    }

    public void onAdapterTurnedOff() {
        runtime.checkStateThread();
        finishTerminal(false, YoBleErrorMapper.failure(
                BleErrorCode.BLUETOOTH_DISABLED, MODULE, "adapter turned off", deviceId), true);
    }

    public void onPlatformBondStateChanged(@NonNull BleBondState newState) {
        runtime.checkStateThread();
        bondState = newState;
        publish();
        notifyConnectionObservers(observer -> observer.onBondStateChanged(deviceId, newState));
    }

    @NonNull
    public BleConnectionSnapshot snapshot() {
        return new BleConnectionSnapshot(deviceId, stateMachine.state(), currentMtu, currentPhy,
                connectionParameters, bondState, lastRssi, retryAttempt, currentDatabase != null,
                lastFailure, nowMillis());
    }

    public int publishedPendingCount() {
        return operationQueue.publishedPendingCount();
    }

    @NonNull
    public YoBleGattOperationHandler gattOperationHandler() {
        return gattHandler;
    }

    @NonNull
    public YoBleLinkTuner linkTuner() {
        return linkTuner;
    }

    @NonNull
    public YoBleReliableWriteCoordinator reliableWriteCoordinator() {
        return reliableWrite;
    }

    @NonNull
    public YoBleLongWriteCoordinator longWriteCoordinator() {
        return longWrite;
    }

    @NonNull
    public YoBleRequestResponseCoordinator requestResponseCoordinator() {
        return requestResponse;
    }

    @NonNull
    public YoBleServiceDiscoveryManager serviceDiscoveryManager() {
        return discoveryManager;
    }

    @NonNull
    public YoBleNotificationRouter notificationRouter() {
        return notificationRouter;
    }

    private void launchConnectAttempt() {
        if (userDisconnected || ended || !capabilityInspector.isAdapterEnabled()) {
            if (!userDisconnected && !ended) {
                finishTerminal(false, YoBleErrorMapper.failure(
                        BleErrorCode.BLUETOOTH_DISABLED, MODULE, "adapter disabled", deviceId), true);
            }
            return;
        }
        long gen = ++generation;
        boolean platformAuto = backgroundAutoConnect;
        if (!platformAuto && connectionConfig.connectTimeoutMillis() > 0L) {
            connectTimeout = runtime.stateDelayed(() -> {
                if (gen == generation && stateMachine.state() == BleConnectionState.CONNECTING) {
                    failAttempt(gen, false, YoBleErrorMapper.failure(
                            BleErrorCode.CONNECT_TIMEOUT, MODULE, "connect timed out", deviceId), true);
                }
            }, connectionConfig.connectTimeoutMillis());
        }
        YoBleConnectionPlatform.BleGattHandle opened;
        try {
            opened = connectionPlatform.connectGatt(deviceId, platformAuto, new SessionListener(gen));
        } catch (SecurityException security) {
            finishTerminal(false, YoBleErrorMapper.failure(
                    BleErrorCode.PERMISSION_CONNECT_DENIED, MODULE, "connectGatt security exception",
                    security, BleFailure.NO_RAW_STATUS, deviceId, null, null), true);
            return;
        }
        if (opened == null) {
            failAttempt(gen, false, YoBleErrorMapper.failure(
                    BleErrorCode.CONNECT_FAILED, MODULE, "connectGatt returned null", deviceId), true);
            return;
        }
        handle = opened;
    }

    private void onConnected(long gen) {
        if (gen != generation || userDisconnected) {
            return;
        }
        cancel(connectTimeout);
        connectTimeout = null;
        transition(BleConnectionState.CONNECTED, "connected");
        publish();
        if (connectionConfig.bondPolicy() == BleBondPolicy.ENSURE_BONDED_BEFORE_DISCOVERY
                && bondState != BleBondState.BONDED) {
            transition(BleConnectionState.BONDING_WAIT, "bond");
            publish();
            bondController.createBond(deviceId, new BleValueObserver<BleDeviceSnapshot>() {
                @Override
                public void onSuccess(@NonNull BleDeviceSnapshot value) {
                    runtime.state(() -> beginDiscovery());
                }

                @Override
                public void onFailed(@NonNull BleFailure failure) {
                    runtime.state(() -> finishTerminal(false, failure, true));
                }
            });
            return;
        }
        beginDiscovery();
    }

    private void beginDiscovery() {
        if (!connectionConfig.autoDiscoverServices()) {
            beginInitializing();
            return;
        }
        transition(BleConnectionState.DISCOVERING_SERVICES, "discover");
        publish();
        discoveryManager.discover(connectionConfig.refreshCacheBeforeDiscovery(),
                connectionConfig.refreshCacheBeforeDiscovery(),
                new BleValueObserver<BleGattDatabaseSnapshot>() {
                    @Override
                    public void onSuccess(@NonNull BleGattDatabaseSnapshot value) {
                        runtime.state(() -> beginInitializing());
                    }

                    @Override
                    public void onFailed(@NonNull BleFailure failure) {
                        runtime.state(() -> finishTerminal(false, failure, true));
                    }
                });
    }

    private void beginInitializing() {
        transition(BleConnectionState.INITIALIZING, "init");
        publish();
        Integer mtu = connectionConfig.requestMtu();
        if (mtu == null) {
            runPriority();
            return;
        }
        linkTuner.requestMtu(mtu, new BleValueObserver<Integer>() {
            @Override
            public void onSuccess(@NonNull Integer value) {
                runtime.state(YoBleGattSession.this::runPriority);
            }

            @Override
            public void onFailed(@NonNull BleFailure failure) {
                runtime.state(YoBleGattSession.this::runPriority);
            }
        });
    }

    private void runPriority() {
        BleConnectionPriority priority = connectionConfig.preferredConnectionPriority();
        if (priority == null) {
            runPhy();
            return;
        }
        linkTuner.requestConnectionPriority(priority, new BleValueObserver<Void>() {
            @Override
            public void onSuccess(@Nullable Void value) {
                runtime.state(YoBleGattSession.this::runPhy);
            }

            @Override
            public void onFailed(@NonNull BleFailure failure) {
                runtime.state(YoBleGattSession.this::runPhy);
            }
        });
    }

    private void runPhy() {
        BlePhyConfig phy = connectionConfig.preferredPhy();
        if (phy == null) {
            finishInit();
            return;
        }
        linkTuner.setPreferredPhy(phy.txPhy(), phy.rxPhy(), phy.phyOptions(), new BleValueObserver<BlePhyConfig>() {
            @Override
            public void onSuccess(@NonNull BlePhyConfig value) {
                runtime.state(YoBleGattSession.this::finishInit);
            }

            @Override
            public void onFailed(@NonNull BleFailure failure) {
                runtime.state(YoBleGattSession.this::finishInit);
            }
        });
    }

    private void finishInit() {
        notificationRouter.restoreSubscriptions(() -> {
            retryAttempt = 0;
            backgroundAutoConnect = false;
            transition(BleConnectionState.READY, "ready");
            publish();
        });
    }

    private void onDisconnected(long gen, int status) {
        if (gen != generation) {
            return;
        }
        if (stateMachine.state() == BleConnectionState.DISCONNECTING) {
            closeHandle();
            transition(BleConnectionState.DISCONNECTED, "closed");
            publish();
            markEnded();
            return;
        }
        boolean wasUp = stateMachine.isConnected();
        BleFailure reason = status == 0
                ? YoBleErrorMapper.failure(BleErrorCode.REMOTE_TERMINATED, MODULE, "remote disconnected", deviceId)
                : YoBleErrorMapper.fromHciStatus(status, MODULE, deviceId);
        lastFailure = reason;
        boolean retryConnect = !wasUp && !userDisconnected && retryAttempt < connectionConfig.maxRetryCount()
                && (YoBleHciStatus.isRetryableConnectFailure(status) || reason.errorCode() == BleErrorCode.CONNECT_FAILED);
        boolean auto = YoBleReconnectPolicy.shouldAutoReconnectOnUnexpectedDrop(
                wasUp, retryAttempt, connectionConfig, userDisconnected,
                capabilityInspector.isAdapterEnabled(), runtime.isReleased(), status);
        releaseGatt(reason, !(retryConnect || auto));
        if (retryConnect || auto) {
            if (auto) {
                backgroundAutoConnect = true;
            } else {
                retryAttempt++;
            }
            transition(BleConnectionState.DISCONNECTED, "retry-wait");
            publish();
            long wait = YoBleReconnectPolicy.computeWaitMillis(Math.max(1, retryAttempt), connectionConfig,
                    nowMillis(), lastCloseAtMillis);
            retryHandle = runtime.stateDelayed(() -> {
                transition(BleConnectionState.CONNECTING, "retry");
                publish();
                launchConnectAttempt();
            }, wait);
            return;
        }
        finishTerminal(false, reason, true);
    }

    private void failAttempt(long gen, boolean wasUp, @NonNull BleFailure reason, boolean mayRetry) {
        if (gen != generation || userDisconnected || ended) {
            return;
        }
        lastFailure = reason;
        boolean retry = mayRetry && !wasUp && retryAttempt < connectionConfig.maxRetryCount();
        releaseGatt(reason, !retry);
        if (retry) {
            retryAttempt++;
            transition(BleConnectionState.DISCONNECTED, "retry-wait");
            publish();
            retryHandle = runtime.stateDelayed(() -> {
                transition(BleConnectionState.CONNECTING, "retry");
                publish();
                launchConnectAttempt();
            }, YoBleReconnectPolicy.computeWaitMillis(retryAttempt, connectionConfig, nowMillis(), lastCloseAtMillis));
            return;
        }
        finishTerminal(false, reason, true);
    }

    private void gracefulDisconnect() {
        transition(BleConnectionState.DISCONNECTING, "user");
        publish();
        YoBleConnectionPlatform.BleGattHandle local = handle;
        handle = null;
        operationQueue.clearAll(YoBleErrorMapper.failure(
                BleErrorCode.LOCAL_TERMINATED, MODULE, "user disconnect", deviceId));
        notificationRouter.endSession(true, null);
        discoveryManager.reset();
        reliableWrite.reset();
        linkTuner.cancelAllFallbacks();
        if (local != null) {
            local.disconnect();
        }
        closeTimeout = runtime.stateDelayed(() -> {
            if (stateMachine.state() == BleConnectionState.DISCONNECTING) {
                closeLocal(local);
                transition(BleConnectionState.DISCONNECTED, "close-timeout");
                publish();
                markEnded();
            }
        }, DISCONNECT_CLOSE_TIMEOUT_MS);
    }

    private void finishTerminal(boolean user, @NonNull BleFailure reason, boolean notifyFailure) {
        if (ended) {
            return;
        }
        lastFailure = reason;
        releaseGatt(reason, true);
        transition(user ? BleConnectionState.DISCONNECTED : BleConnectionState.FAILED, "terminal");
        publish();
        if (notifyFailure && !user) {
            notifyConnectionObservers(observer -> observer.onConnectionFailed(reason));
        }
        markEnded();
    }

    private void releaseGatt(@NonNull BleFailure reason, boolean terminal) {
        cancel(connectTimeout);
        connectTimeout = null;
        YoBleConnectionPlatform.BleGattHandle local = handle;
        handle = null;
        generation++;
        operationQueue.clearAll(reason);
        discoveryManager.reset();
        reliableWrite.reset();
        linkTuner.cancelAllFallbacks();
        currentDatabase = null;
        snapshotStore.clearGattDatabase(deviceId, nowMillis());
        if (terminal) {
            notificationRouter.endSession(userDisconnected, userDisconnected ? null : reason);
        } else {
            notificationRouter.onLinkLost();
        }
        closeLocal(local);
    }

    private void closeHandle() {
        closeLocal(handle);
        handle = null;
    }

    private void closeLocal(@Nullable YoBleConnectionPlatform.BleGattHandle local) {
        if (local == null || local.isClosed()) {
            return;
        }
        try {
            local.disconnect();
        } catch (Throwable ignored) {
        }
        try {
            local.close();
        } catch (Throwable ignored) {
        }
        lastCloseAtMillis = nowMillis();
        closeTimestampSink.accept(deviceId, lastCloseAtMillis);
    }

    private void markEnded() {
        ended = true;
        cancelTimers();
        List<Runnable> waiters = new ArrayList<>(endedWaiters);
        endedWaiters.clear();
        for (Runnable waiter : waiters) {
            waiter.run();
        }
    }

    private void transition(@NonNull BleConnectionState next, @NonNull String event) {
        stateMachine.transitionTo(next, event);
    }

    private void publish() {
        BleConnectionSnapshot snap = snapshot();
        snapshotStore.recordConnectionSnapshot(snap);
        notifyConnectionObservers(observer -> observer.onConnectionStateChanged(snap));
    }

    private void cancelTimers() {
        cancel(connectTimeout);
        cancel(retryHandle);
        cancel(closeTimeout);
        connectTimeout = null;
        retryHandle = null;
        closeTimeout = null;
    }

    private static void cancel(@Nullable YoTimeoutHandle handle) {
        if (handle != null) {
            handle.cancel();
        }
    }

    private long remainingCloseGap() {
        if (lastCloseAtMillis <= 0L) {
            return 0L;
        }
        return Math.max(0L, connectionConfig.closeDelayMillis() - (nowMillis() - lastCloseAtMillis));
    }

    @Override
    @NonNull
    public BleDeviceId deviceId() {
        return deviceId;
    }

    @Override
    @NonNull
    public YoBleRuntime runtime() {
        return runtime;
    }

    @Override
    @NonNull
    public BleConfig bleConfig() {
        return bleConfig;
    }

    @Override
    @NonNull
    public YoBleCapabilityInspector capabilityInspector() {
        return capabilityInspector;
    }

    @Override
    @NonNull
    public YoBleDeviceSnapshotStore snapshotStore() {
        return snapshotStore;
    }

    @Override
    @NonNull
    public YoBleTimelineRecorder timeline() {
        return timeline;
    }

    @Override
    public long nowMillis() {
        return clockMillis.getAsLong();
    }

    @Override
    public long operationTimeoutMillis() {
        return connectionConfig.operationTimeoutMillis();
    }

    @Override
    public long bondedDiscoveryDelayMillis() {
        return connectionConfig.bondedDiscoveryDelayMillis();
    }

    @Override
    public boolean isConnected() {
        return !ended && stateMachine.isConnected();
    }

    @Override
    @NonNull
    public BleBondState bondState() {
        return bondState;
    }

    @Override
    public int currentMtu() {
        return currentMtu;
    }

    @Override
    @Nullable
    public Integer overriddenMtu() {
        return overriddenMtu;
    }

    @Override
    public void onMtuUpdated(int mtu) {
        currentMtu = mtu;
    }

    @Override
    public void onMtuOverridden(int mtu) {
        overriddenMtu = mtu;
    }

    @Override
    public void onPhyUpdated(@NonNull BlePhyConfig phy) {
        currentPhy = phy;
    }

    @Override
    public void onConnectionParametersUpdated(@NonNull BleConnectionParameters parameters) {
        connectionParameters = parameters;
    }

    @Override
    public void onRssiUpdated(int rssi) {
        lastRssi = rssi;
    }

    @Override
    @Nullable
    public BleGattDatabaseSnapshot currentDatabase() {
        return currentDatabase;
    }

    @Override
    public void onDatabaseUpdated(@NonNull BleGattDatabaseSnapshot database) {
        currentDatabase = database;
    }

    @Override
    public void onDatabaseCleared() {
        currentDatabase = null;
    }

    @Override
    @Nullable
    public YoBleGattTransport transport() {
        return handle == null || handle.isClosed() ? null : handle.transport();
    }

    @Override
    @NonNull
    public YoBleGattOperationQueue operationQueue() {
        return operationQueue;
    }

    @Override
    public void notifyConnectionObservers(@NonNull Consumer<BleConnectionObserver> emitter) {
        runtime.callback(() -> {
            for (BleConnectionObserver observer : sessionObservers) {
                emitter.accept(observer);
            }
            for (BleConnectionObserver observer : globalObservers) {
                emitter.accept(observer);
            }
        });
    }

    @Override
    public void recoverFromAuthError(@NonNull BleFailure failure,
                                     @NonNull Runnable onRecovered,
                                     @NonNull Runnable onGiveUp) {
        BleBondPolicy policy = connectionConfig.bondPolicy();
        boolean allow = policy == BleBondPolicy.AUTO_BOND_ON_AUTH_ERROR
                || (policy == BleBondPolicy.NONE && capabilityInspector.sdkInt() < 26);
        if (!allow || !YoBleGattStatus.isAuthOrEncryptionError(failure.rawStatusCode())) {
            onGiveUp.run();
            return;
        }
        bondController.createBond(deviceId, new BleValueObserver<BleDeviceSnapshot>() {
            @Override
            public void onSuccess(@NonNull BleDeviceSnapshot value) {
                runtime.state(onRecovered);
            }

            @Override
            public void onFailed(@NonNull BleFailure bondFailure) {
                runtime.state(onGiveUp);
            }
        });
    }

    private final class SessionListener implements YoBleConnectionPlatform.PlatformListener {
        private final long gen;

        SessionListener(long gen) {
            this.gen = gen;
        }

        private void post(@NonNull Runnable action) {
            runtime.state(() -> {
                if (gen != generation && stateMachine.state() != BleConnectionState.DISCONNECTING) {
                    return;
                }
                action.run();
            });
        }

        @Override
        public void onConnectionStateChange(int status, int newState) {
            post(() -> {
                if (newState == YoBleConnectionPlatform.STATE_CONNECTED && status == 0) {
                    onConnected(gen);
                } else {
                    onDisconnected(gen, status);
                }
            });
        }

        @Override
        public void onServicesDiscovered(int status) {
            post(() -> discoveryManager.onPlatformServicesDiscovered(status));
        }

        @Override
        public void onServiceChanged() {
            post(discoveryManager::onServiceChanged);
        }

        @Override
        public void onCharacteristicRead(@NonNull BleGattTarget target, @NonNull byte[] value, int status) {
            post(() -> complete(YoBleGattOperationQueue.OpKind.READ_CHAR, target, value, status, BleErrorCode.READ_FAILED, "read"));
        }

        @Override
        public void onCharacteristicWrite(@NonNull BleGattTarget target, @Nullable byte[] echoedValue, int status) {
            post(() -> complete(YoBleGattOperationQueue.OpKind.WRITE_CHAR, target, echoedValue, status, BleErrorCode.WRITE_FAILED, "write"));
        }

        @Override
        public void onCharacteristicChanged(@NonNull BleGattTarget target, @NonNull byte[] value) {
            post(() -> notificationRouter.dispatchCharacteristicChanged(target, value));
        }

        @Override
        public void onDescriptorRead(@NonNull BleGattTarget target, @NonNull byte[] value, int status) {
            post(() -> complete(YoBleGattOperationQueue.OpKind.READ_DESC, target, value, status, BleErrorCode.READ_FAILED, "readDescriptor"));
        }

        @Override
        public void onDescriptorWrite(@NonNull BleGattTarget target, int status) {
            post(() -> complete(YoBleGattOperationQueue.OpKind.WRITE_DESC, target, null, status, BleErrorCode.NOTIFY_FAILED, "writeDescriptor"));
        }

        @Override
        public void onMtuChanged(int mtu, int status) {
            post(() -> linkTuner.onPlatformMtuChanged(mtu, status));
        }

        @Override
        public void onPhyUpdate(@NonNull BlePhyConfig phy, int status) {
            post(() -> linkTuner.onPlatformPhyUpdate(phy, status));
        }

        @Override
        public void onPhyRead(@NonNull BlePhyConfig phy, int status) {
            post(() -> linkTuner.onPlatformPhyRead(phy, status));
        }

        @Override
        public void onConnectionUpdated(int interval, int latency, int timeout, int status) {
            post(() -> linkTuner.onPlatformConnectionUpdated(interval, latency, timeout, status));
        }

        @Override
        public void onReadRemoteRssi(int rssi, int status) {
            post(() -> linkTuner.onPlatformRssiRead(rssi, status));
        }

        @Override
        public void onReliableWriteCompleted(int status) {
            post(() -> {
                BleFailure failure = status == 0 ? null : YoBleErrorMapper.fromGattStatus(
                        status, BleErrorCode.WRITE_FAILED, MODULE, deviceId, null, "reliableWrite");
                if (!operationQueue.complete(YoBleGattOperationQueue.OpKind.RELIABLE_ABORT, null, null, failure)) {
                    operationQueue.complete(YoBleGattOperationQueue.OpKind.RELIABLE_EXECUTE, null, null, failure);
                }
            });
        }

        private void complete(@NonNull YoBleGattOperationQueue.OpKind kind,
                              @NonNull BleGattTarget target,
                              @Nullable Object payload,
                              int status,
                              @NonNull BleErrorCode fallback,
                              @NonNull String label) {
            BleFailure failure = status == 0 ? null : YoBleErrorMapper.fromGattStatus(
                    status, fallback, MODULE, deviceId, target, label);
            operationQueue.complete(kind, target, status == 0 ? payload : null, failure);
        }
    }
}
