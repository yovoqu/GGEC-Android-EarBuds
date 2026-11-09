package com.yovo.lib_bluetooth.internal.ble.connection;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.ble.device.YoBleCapabilityInspector;
import com.yovo.lib_bluetooth.internal.ble.device.YoBleDeviceSnapshotStore;
import com.yovo.lib_bluetooth.internal.ble.diagnostics.YoBleTimelineRecorder;
import com.yovo.lib_bluetooth.internal.ble.error.YoBleErrorMapper;
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

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * 单个 BLE 设备的独立 GATT 会话上下文与生命周期编排器。
 */
public final class YoBleGattSession implements YoBleSessionContext {

    private static final String MODULE = "ble-session";

    private final BleDeviceId deviceId;
    private final YoBleRuntime runtime;
    private final BleConfig bleConfig;
    private final BleConnectionConfig connectionConfig;
    private final YoBleCapabilityInspector capabilityInspector;
    private final YoBleConnectionPlatform connectionPlatform;
    private final YoBleBondController bondController;
    private final YoBleDeviceSnapshotStore snapshotStore;
    private final YoBleTimelineRecorder timeline;
    private final LongSupplier clockMillis;
    private final CopyOnWriteArrayList<BleConnectionObserver> globalObservers;
    private final CopyOnWriteArrayList<BleConnectionObserver> sessionObservers = new CopyOnWriteArrayList<>();
    private final BiConsumer<BleDeviceId, Long> closeTimestampSink;

    private final YoBleConnectionStateMachine stateMachine = new YoBleConnectionStateMachine();
    private final YoBleGattOperationQueue operationQueue;
    private final YoBleNotificationRouter notificationRouter;
    private final YoBleServiceDiscoveryManager serviceDiscoveryManager;
    private final YoBleReliableWriteCoordinator reliableWriteCoordinator;
    private final YoBleGattOperationHandler gattOperationHandler;
    private final YoBleLinkTuner linkTuner;
    private final YoBleLongWriteCoordinator longWriteCoordinator;
    private final YoBleRequestResponseCoordinator requestResponseCoordinator;

    private final AtomicLong attemptSeq = new AtomicLong();
    private long activeAttemptGen = 0L;

    @Nullable
    private YoBleConnectionPlatform.BleGattHandle gattHandle;
    @Nullable
    private YoTimeoutHandle connectTimeoutHandle;
    @Nullable
    private YoTimeoutHandle pendingRetryHandle;

    private int currentMtu = BleConnectionSnapshot.DEFAULT_ATT_MTU;
    @Nullable
    private Integer overriddenMtu;
    @NonNull
    private BlePhyConfig currentPhy = BlePhyConfig.le1m();
    @Nullable
    private BleConnectionParameters connectionParameters;
    @NonNull
    private BleBondState bondState = BleBondState.UNKNOWN;
    @Nullable
    private Integer lastRssi;
    private int retryAttempt = 0;
    @Nullable
    private BleGattDatabaseSnapshot currentDatabase;
    @Nullable
    private BleFailure lastFailure;

    private long lastCloseAtMillis;
    private boolean userDisconnected = false;
    private boolean released = false;
    private volatile boolean authRetryUsed = false;

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
        this.lastCloseAtMillis = lastCloseAtMillis;
        this.closeTimestampSink = closeTimestampSink;
        this.sessionObservers.add(initialObserver);

        this.operationQueue = new YoBleGattOperationQueue(
                deviceId, runtime, bleConfig.gattOperationQueueCapacity());
        this.notificationRouter = new YoBleNotificationRouter(this);
        this.serviceDiscoveryManager = new YoBleServiceDiscoveryManager(this);
        this.reliableWriteCoordinator = new YoBleReliableWriteCoordinator(this);
        this.gattOperationHandler = new YoBleGattOperationHandler(this, reliableWriteCoordinator);
        this.linkTuner = new YoBleLinkTuner(this);
        this.longWriteCoordinator = new YoBleLongWriteCoordinator(this, gattOperationHandler);
        this.requestResponseCoordinator = new YoBleRequestResponseCoordinator(this, gattOperationHandler);
        this.bondState = bondController.getBondState(deviceId);
    }

    public void attachObserver(@NonNull BleConnectionObserver observer) {
        sessionObservers.addIfAbsent(observer);
    }

    public void detachObserver(@NonNull BleConnectionObserver observer) {
        sessionObservers.remove(observer);
    }

    public void startConnect() {
        runtime.checkStateThread();
        userDisconnected = false;
        lastFailure = null;
        retryAttempt = 0;

        long now = clockMillis.getAsLong();
        long closeGapRemaining = lastCloseAtMillis > 0L
                ? Math.max(0L, connectionConfig.closeDelayMillis() - Math.max(0L, now - lastCloseAtMillis))
                : 0L;

        if (stateMachine.transitionTo(BleConnectionState.CONNECTING, "connect")) {
            publishStateChange();
        }
        if (closeGapRemaining > 0L) {
            timeline.record(MODULE, "waiting " + closeGapRemaining + "ms close-gap before connectGatt: " + deviceId);
            pendingRetryHandle = runtime.scheduler().schedule(
                    () -> runtime.state(this::launchConnectAttempt),
                    closeGapRemaining);
        } else {
            launchConnectAttempt();
        }
    }

    public void userDisconnect() {
        runtime.checkStateThread();
        if (userDisconnected && (stateMachine.state() == BleConnectionState.DISCONNECTED
                || stateMachine.state() == BleConnectionState.FAILED)) {
            return;
        }
        userDisconnected = true;
        cancelTimers();
        timeline.record(MODULE, "userDisconnect: " + deviceId);

        BleFailure cancelReason = YoBleErrorMapper.failure(
                BleErrorCode.LOCAL_TERMINATED, MODULE,
                "Disconnected by local client", deviceId);
        teardownGattAndQueues(cancelReason);

        if (stateMachine.state() != BleConnectionState.DISCONNECTED
                && stateMachine.state() != BleConnectionState.FAILED) {
            if (stateMachine.transitionTo(BleConnectionState.DISCONNECTING, "userDisconnect")) {
                publishStateChange();
            }
            if (stateMachine.transitionTo(BleConnectionState.DISCONNECTED, "userDisconnectDone")) {
                publishStateChange();
            }
        }
    }

    public void onAdapterTurnedOff() {
        runtime.checkStateThread();
        if (stateMachine.state() == BleConnectionState.IDLE
                || stateMachine.state() == BleConnectionState.DISCONNECTED
                || stateMachine.state() == BleConnectionState.FAILED) {
            cancelTimers();
            return;
        }
        cancelTimers();
        userDisconnected = false;
        BleFailure failure = YoBleErrorMapper.failure(
                BleErrorCode.BLUETOOTH_DISABLED, MODULE,
                "Bluetooth adapter turned off; forcing GATT close", deviceId);
        this.lastFailure = failure;
        timeline.record(MODULE, "adapter OFF -> immediate cleanup for " + deviceId);
        teardownGattAndQueues(failure);
        if (stateMachine.state() != BleConnectionState.DISCONNECTED) {
            stateMachine.transitionTo(BleConnectionState.DISCONNECTING, "adapterOff");
            stateMachine.transitionTo(BleConnectionState.DISCONNECTED, "adapterOff");
            publishStateChange();
        }
        notifyFailure(failure);
    }

    public void onPlatformBondStateChanged(@NonNull BleBondState newState) {
        runtime.checkStateThread();
        this.bondState = newState;
        recordSnapshot();
        notifyConnectionObservers(obs -> obs.onBondStateChanged(deviceId, newState));
    }

    public void releaseSession() {
        runtime.checkStateThread();
        if (released) {
            return;
        }
        released = true;
        userDisconnect();
        sessionObservers.clear();
    }

    @NonNull
    public BleConnectionSnapshot snapshot() {
        return new BleConnectionSnapshot(
                deviceId,
                stateMachine.state(),
                currentMtu,
                currentPhy,
                connectionParameters,
                bondState,
                lastRssi,
                retryAttempt,
                currentDatabase != null,
                lastFailure,
                clockMillis.getAsLong());
    }

    @NonNull
    public BleConnectionConfig connectionConfig() {
        return connectionConfig;
    }

    @NonNull
    public YoBleServiceDiscoveryManager serviceDiscoveryManager() {
        return serviceDiscoveryManager;
    }

    @NonNull
    public YoBleGattOperationHandler gattOperationHandler() {
        return gattOperationHandler;
    }

    @NonNull
    public YoBleLinkTuner linkTuner() {
        return linkTuner;
    }

    @NonNull
    public YoBleReliableWriteCoordinator reliableWriteCoordinator() {
        return reliableWriteCoordinator;
    }

    @NonNull
    public YoBleLongWriteCoordinator longWriteCoordinator() {
        return longWriteCoordinator;
    }

    @NonNull
    public YoBleRequestResponseCoordinator requestResponseCoordinator() {
        return requestResponseCoordinator;
    }

    // ── YoBleSessionContext ─────────────────────────────────────────

    @NonNull
    @Override
    public BleDeviceId deviceId() {
        return deviceId;
    }

    @NonNull
    @Override
    public YoBleRuntime runtime() {
        return runtime;
    }

    @NonNull
    @Override
    public BleConfig bleConfig() {
        return bleConfig;
    }

    @NonNull
    @Override
    public YoBleCapabilityInspector capabilityInspector() {
        return capabilityInspector;
    }

    @NonNull
    @Override
    public YoBleDeviceSnapshotStore snapshotStore() {
        return snapshotStore;
    }

    @NonNull
    @Override
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
        return !released && !userDisconnected && stateMachine.isConnected();
    }

    @Override
    public boolean isReady() {
        return !released && !userDisconnected && stateMachine.isReady();
    }

    @NonNull
    @Override
    public BleBondState bondState() {
        return bondState;
    }

    @Override
    public int currentMtu() {
        return currentMtu;
    }

    @Nullable
    @Override
    public Integer overriddenMtu() {
        return overriddenMtu;
    }

    @Override
    public void onMtuUpdated(int mtu) {
        this.currentMtu = Math.max(BleConnectionSnapshot.DEFAULT_ATT_MTU, mtu);
        recordSnapshot();
    }

    @Override
    public void onMtuOverridden(int mtu) {
        this.overriddenMtu = Math.max(BleConnectionSnapshot.DEFAULT_ATT_MTU, mtu);
        this.currentMtu = this.overriddenMtu;
        recordSnapshot();
    }

    @Override
    public void onPhyUpdated(@NonNull BlePhyConfig phy) {
        this.currentPhy = phy;
        recordSnapshot();
    }

    @Override
    public void onConnectionParametersUpdated(@NonNull BleConnectionParameters parameters) {
        this.connectionParameters = parameters;
        recordSnapshot();
    }

    @Override
    public void onRssiUpdated(int rssi) {
        this.lastRssi = rssi;
        recordSnapshot();
    }

    @Nullable
    @Override
    public BleGattDatabaseSnapshot currentDatabase() {
        return currentDatabase;
    }

    @Override
    public void onDatabaseUpdated(@NonNull BleGattDatabaseSnapshot database) {
        this.currentDatabase = database;
        recordSnapshot();
    }

    @Override
    public void onDatabaseCleared() {
        this.currentDatabase = null;
        recordSnapshot();
    }

    @Nullable
    @Override
    public YoBleGattTransport transport() {
        YoBleConnectionPlatform.BleGattHandle h = gattHandle;
        return h == null || h.isClosed() ? null : h.transport();
    }

    @NonNull
    @Override
    public YoBleGattOperationQueue operationQueue() {
        return operationQueue;
    }

    @NonNull
    @Override
    public YoBleNotificationRouter notificationRouter() {
        return notificationRouter;
    }

    @Override
    public void notifyConnectionObservers(@NonNull Consumer<BleConnectionObserver> emitter) {
        for (BleConnectionObserver obs : sessionObservers) {
            runtime.callback(() -> emitter.accept(obs));
        }
        for (BleConnectionObserver obs : globalObservers) {
            runtime.callback(() -> emitter.accept(obs));
        }
    }

    @Override
    public boolean handleAuthErrorIfConfigured(@NonNull BleFailure failure,
                                               @NonNull Runnable retryAction) {
        if (authRetryUsed || !isConnected()) {
            return false;
        }
        boolean policyMatch = connectionConfig.bondPolicy() == BleBondPolicy.AUTO_BOND_ON_AUTH_ERROR
                || (connectionConfig.bondPolicy() == BleBondPolicy.NONE
                && capabilityInspector.sdkInt() < 26);
        if (!policyMatch) {
            return false;
        }
        authRetryUsed = true;
        timeline.record(MODULE, "ATT auth error (" + failure.rawStatusCode()
                + ") -> triggering createBond & retry for " + deviceId);
        bondController.createBond(deviceId, new BleValueObserver<BleDeviceSnapshot>() {
            @Override
            public void onSuccess(@NonNull BleDeviceSnapshot value) {
                runtime.state(() -> {
                    bondState = BleBondState.BONDED;
                    if (isConnected()) {
                        retryAction.run();
                    }
                });
            }

            @Override
            public void onFailed(@NonNull BleFailure bondFailure) {
                runtime.state(() -> {
                    if (isConnected()) {
                        // 重跑一次触发原始操作以释放 authRetryUsed 并让其正常上报失败给调用方
                        retryAction.run();
                    }
                });
            }
        });
        return true;
    }

    // ── 内部连接、状态迁移与平台事件处理 ────────────────────────────

    private void launchConnectAttempt() {
        runtime.checkStateThread();
        if (released || userDisconnected) {
            return;
        }
        if (!capabilityInspector.isAdapterEnabled()) {
            failConnection(YoBleErrorMapper.failure(
                    BleErrorCode.BLUETOOTH_DISABLED, MODULE,
                    "Bluetooth adapter disabled before connectGatt", deviceId), true);
            return;
        }

        if (stateMachine.state() == BleConnectionState.DISCONNECTED
                || stateMachine.state() == BleConnectionState.FAILED) {
            if (stateMachine.transitionTo(BleConnectionState.CONNECTING, "retryConnect")) {
                publishStateChange();
            }
        }

        long gen = attemptSeq.incrementAndGet();
        activeAttemptGen = gen;
        authRetryUsed = false;

        if (connectionConfig.connectTimeoutMillis() > 0L) {
            armConnectTimeout(gen);
        }

        timeline.record(MODULE, "connectGatt attempt=" + retryAttempt
                + ", autoConnect=" + connectionConfig.autoConnect() + " device=" + deviceId);

        YoBleConnectionPlatform.BleGattHandle handle = connectionPlatform.connectGatt(
                deviceId, connectionConfig.autoConnect(), new SessionPlatformListener(gen));

        if (handle == null) {
            handleConnectOrDropFailure(gen, false, YoBleHciStatus.GATT_ERROR_133,
                    YoBleErrorMapper.failure(BleErrorCode.CONNECT_FAILED, MODULE,
                            "connectGatt returned null", deviceId));
            return;
        }
        this.gattHandle = handle;
    }

    private void armConnectTimeout(long gen) {
        if (connectTimeoutHandle != null) {
            connectTimeoutHandle.cancel();
        }
        connectTimeoutHandle = runtime.scheduler().schedule(
                () -> runtime.state(() -> {
                    if (released || userDisconnected || gen != activeAttemptGen) {
                        return;
                    }
                    if (stateMachine.state() != BleConnectionState.CONNECTING) {
                        return;
                    }
                    timeline.record(MODULE, "connect timeout after "
                            + connectionConfig.connectTimeoutMillis() + "ms: " + deviceId);
                    BleFailure timeoutFailure = YoBleErrorMapper.failure(
                            BleErrorCode.CONNECT_TIMEOUT, MODULE,
                            "BLE connect timed out after " + connectionConfig.connectTimeoutMillis() + "ms",
                            deviceId);
                    handleConnectOrDropFailure(gen, false, YoBleHciStatus.CONNECTION_TIMEOUT, timeoutFailure);
                }),
                connectionConfig.connectTimeoutMillis());
    }

    private void onPlatformConnected(long gen) {
        if (released || userDisconnected || gen != activeAttemptGen) {
            return;
        }
        if (connectTimeoutHandle != null) {
            connectTimeoutHandle.cancel();
            connectTimeoutHandle = null;
        }
        bondState = bondController.getBondState(deviceId);
        if (!stateMachine.transitionTo(BleConnectionState.CONNECTED, "onConnected")) {
            return;
        }
        timeline.record(MODULE, "GATT connected: " + deviceId + ", bond=" + bondState);
        publishStateChange();

        if (connectionConfig.bondPolicy() == BleBondPolicy.ENSURE_BONDED_BEFORE_DISCOVERY
                && bondState != BleBondState.BONDED) {
            stateMachine.transitionTo(BleConnectionState.BONDING_WAIT, "ensureBonded");
            publishStateChange();
            bondController.createBond(deviceId, new BleValueObserver<BleDeviceSnapshot>() {
                @Override
                public void onSuccess(@NonNull BleDeviceSnapshot value) {
                    runtime.state(() -> {
                        if (released || userDisconnected || gen != activeAttemptGen || !isConnected()) {
                            return;
                        }
                        bondState = BleBondState.BONDED;
                        beginDiscoveryPhase(gen);
                    });
                }

                @Override
                public void onFailed(@NonNull BleFailure failure) {
                    runtime.state(() -> {
                        if (released || userDisconnected || gen != activeAttemptGen) {
                            return;
                        }
                        failConnection(failure, false);
                    });
                }
            });
            return;
        }

        beginDiscoveryPhase(gen);
    }

    private void beginDiscoveryPhase(long gen) {
        if (released || userDisconnected || gen != activeAttemptGen || !isConnected()) {
            return;
        }
        if (!connectionConfig.autoDiscoverServices()) {
            beginInitializingPhase(gen);
            return;
        }
        stateMachine.transitionTo(BleConnectionState.DISCOVERING_SERVICES, "discoverServices");
        publishStateChange();
        serviceDiscoveryManager.discoverServices(
                connectionConfig.refreshCacheBeforeDiscovery(),
                connectionConfig.autoHandleServiceChanged(),
                new BleValueObserver<BleGattDatabaseSnapshot>() {
                    @Override
                    public void onSuccess(@NonNull BleGattDatabaseSnapshot value) {
                        runtime.state(() -> {
                            if (released || userDisconnected || gen != activeAttemptGen || !isConnected()) {
                                return;
                            }
                            if (stateMachine.state() == BleConnectionState.DISCOVERING_SERVICES) {
                                beginInitializingPhase(gen);
                            }
                        });
                    }

                    @Override
                    public void onFailed(@NonNull BleFailure failure) {
                        runtime.state(() -> {
                            if (released || userDisconnected || gen != activeAttemptGen) {
                                return;
                            }
                            if (stateMachine.state() == BleConnectionState.DISCOVERING_SERVICES) {
                                failConnection(failure, false);
                            }
                        });
                    }
                });
    }

    private void beginInitializingPhase(long gen) {
        if (released || userDisconnected || gen != activeAttemptGen || !isConnected()) {
            return;
        }
        stateMachine.transitionTo(BleConnectionState.INITIALIZING, "initPipeline");
        publishStateChange();
        runMtuInitStep(gen);
    }

    private void runMtuInitStep(long gen) {
        Integer targetMtu = connectionConfig.requestMtu();
        if (targetMtu == null) {
            runPriorityInitStep(gen);
            return;
        }
        linkTuner.requestMtu(targetMtu, new BleValueObserver<Integer>() {
            @Override
            public void onSuccess(@NonNull Integer value) {
                runtime.state(() -> {
                    if (gen == activeAttemptGen && isConnected()) {
                        runPriorityInitStep(gen);
                    }
                });
            }

            @Override
            public void onFailed(@NonNull BleFailure failure) {
                runtime.state(() -> {
                    timeline.record(MODULE, "init requestMtu non-fatal failure: " + failure.errorCode());
                    if (gen == activeAttemptGen && isConnected()) {
                        runPriorityInitStep(gen);
                    }
                });
            }
        });
    }

    private void runPriorityInitStep(long gen) {
        com.yovo.lib_bluetooth.model.ble.BleConnectionPriority prio =
                connectionConfig.preferredConnectionPriority();
        if (prio == null) {
            runPhyInitStep(gen);
            return;
        }
        linkTuner.requestConnectionPriority(prio, new BleValueObserver<Void>() {
            @Override
            public void onSuccess(@Nullable Void value) {
                runtime.state(() -> {
                    if (gen == activeAttemptGen && isConnected()) {
                        runPhyInitStep(gen);
                    }
                });
            }

            @Override
            public void onFailed(@NonNull BleFailure failure) {
                runtime.state(() -> {
                    timeline.record(MODULE, "init priority non-fatal failure: " + failure.errorCode());
                    if (gen == activeAttemptGen && isConnected()) {
                        runPhyInitStep(gen);
                    }
                });
            }
        });
    }

    private void runPhyInitStep(long gen) {
        BlePhyConfig phy = connectionConfig.preferredPhy();
        if (phy == null || capabilityInspector.sdkInt() < 26) {
            completeInitialization(gen);
            return;
        }
        linkTuner.setPreferredPhy(phy.txPhy(), phy.rxPhy(), phy.phyOptions(),
                new BleValueObserver<BlePhyConfig>() {
                    @Override
                    public void onSuccess(@NonNull BlePhyConfig value) {
                        runtime.state(() -> completeInitialization(gen));
                    }

                    @Override
                    public void onFailed(@NonNull BleFailure failure) {
                        runtime.state(() -> {
                            timeline.record(MODULE, "init setPreferredPhy non-fatal failure: " + failure.errorCode());
                            completeInitialization(gen);
                        });
                    }
                });
    }

    private void completeInitialization(long gen) {
        if (released || userDisconnected || gen != activeAttemptGen || !isConnected()) {
            return;
        }
        if (stateMachine.transitionTo(BleConnectionState.READY, "ready")) {
            timeline.record(MODULE, "GATT session READY: " + deviceId
                    + ", mtu=" + currentMtu
                    + ", phy=" + currentPhy.txPhy() + "/" + currentPhy.rxPhy());
            publishStateChange();
        }
    }

    private void onPlatformDisconnected(long gen, int status) {
        if (released || gen != activeAttemptGen) {
            return;
        }
        cancelTimers();
        boolean wasConnectedOrReady = stateMachine.isConnected();
        if (userDisconnected) {
            teardownGattAndQueues(YoBleErrorMapper.failure(
                    BleErrorCode.LOCAL_TERMINATED, MODULE, "Disconnected locally", deviceId));
            stateMachine.transitionTo(BleConnectionState.DISCONNECTED, "platformDisconnectedLocal");
            publishStateChange();
            return;
        }

        BleFailure reason = status == 0
                ? YoBleErrorMapper.failure(BleErrorCode.REMOTE_TERMINATED, MODULE,
                "Peripheral disconnected (status=0)", deviceId)
                : YoBleErrorMapper.fromHciStatus(status, MODULE, deviceId);
        handleConnectOrDropFailure(gen, wasConnectedOrReady, status, reason);
    }

    private void handleConnectOrDropFailure(long gen,
                                            boolean wasConnectedOrReady,
                                            int rawStatus,
                                            @NonNull BleFailure reason) {
        if (released || gen != activeAttemptGen || userDisconnected) {
            return;
        }
        cancelTimers();
        teardownGattAndQueues(reason);

        boolean retryConnect = !wasConnectedOrReady && YoBleReconnectPolicy.shouldRetryOnConnectFailure(
                retryAttempt, rawStatus, connectionConfig, userDisconnected,
                capabilityInspector.isAdapterEnabled(), released);
        boolean autoReconnect = YoBleReconnectPolicy.shouldAutoReconnectOnUnexpectedDrop(
                wasConnectedOrReady, retryAttempt, connectionConfig, userDisconnected,
                capabilityInspector.isAdapterEnabled(), released, rawStatus);

        if (retryConnect || autoReconnect) {
            retryAttempt++;
            if (wasConnectedOrReady && stateMachine.transitionTo(BleConnectionState.DISCONNECTED, "autoReconnectWait")) {
                publishStateChange();
            }
            long waitMs = YoBleReconnectPolicy.computeWaitMillis(
                    retryAttempt, connectionConfig, clockMillis.getAsLong(), lastCloseAtMillis);
            timeline.record(MODULE, "scheduling retry #" + retryAttempt
                    + " in " + waitMs + "ms (rawStatus=" + rawStatus + "): " + deviceId);
            pendingRetryHandle = runtime.scheduler().schedule(
                    () -> runtime.state(this::launchConnectAttempt),
                    waitMs);
            return;
        }

        failConnection(reason, wasConnectedOrReady);
    }

    private void failConnection(@NonNull BleFailure failure, boolean endInDisconnectedState) {
        cancelTimers();
        this.lastFailure = failure;
        teardownGattAndQueues(failure);
        BleConnectionState targetEnd = endInDisconnectedState
                ? BleConnectionState.DISCONNECTED
                : BleConnectionState.FAILED;
        if (stateMachine.state() != targetEnd) {
            stateMachine.transitionTo(targetEnd, "fail:" + failure.errorCode());
            publishStateChange();
        }
        timeline.record(MODULE, "session failed (" + failure.errorCode()
                + ", raw=" + failure.rawStatusCode() + "): " + deviceId);
        notifyFailure(failure);
    }

    private void teardownGattAndQueues(@NonNull BleFailure reason) {
        operationQueue.clearAll(reason);
        YoBleConnectionPlatform.BleGattHandle handle = gattHandle;
        if (reliableWriteCoordinator.isInTransaction() && handle != null && !handle.isClosed()) {
            try {
                handle.transport().abortReliableWrite();
            } catch (Throwable ignored) {
            }
        }
        reliableWriteCoordinator.reset();
        notificationRouter.clearAll();
        serviceDiscoveryManager.reset();
        linkTuner.cancelAllFallbacks();
        currentDatabase = null;
        snapshotStore.clearGattDatabase(deviceId, clockMillis.getAsLong());

        gattHandle = null;
        if (handle != null && !handle.isClosed()) {
            try {
                handle.disconnect();
            } catch (Throwable ignored) {
            }
            try {
                handle.close();
            } catch (Throwable ignored) {
            }
            long now = clockMillis.getAsLong();
            this.lastCloseAtMillis = now;
            closeTimestampSink.accept(deviceId, now);
        }
    }

    private void cancelTimers() {
        if (connectTimeoutHandle != null) {
            connectTimeoutHandle.cancel();
            connectTimeoutHandle = null;
        }
        if (pendingRetryHandle != null) {
            pendingRetryHandle.cancel();
            pendingRetryHandle = null;
        }
    }

    private void publishStateChange() {
        BleConnectionSnapshot snap = recordSnapshot();
        notifyConnectionObservers(obs -> obs.onConnectionStateChanged(snap));
    }

    @NonNull
    private BleConnectionSnapshot recordSnapshot() {
        BleConnectionSnapshot snap = snapshot();
        snapshotStore.recordConnectionSnapshot(snap);
        return snap;
    }

    private void notifyFailure(@NonNull BleFailure failure) {
        notifyConnectionObservers(obs -> obs.onConnectionFailed(failure));
    }

    private final class SessionPlatformListener implements YoBleConnectionPlatform.PlatformListener {
        private final long gen;

        SessionPlatformListener(long gen) {
            this.gen = gen;
        }

        @Override
        public void onConnectionStateChange(int status, int newState) {
            runtime.state(() -> {
                if (gen != activeAttemptGen) {
                    return;
                }
                if (newState == YoBleConnectionPlatform.STATE_CONNECTED && status == 0) {
                    onPlatformConnected(gen);
                } else {
                    onPlatformDisconnected(gen, status);
                }
            });
        }

        @Override
        public void onServicesDiscovered(int status) {
            runtime.state(() -> {
                if (gen == activeAttemptGen) {
                    serviceDiscoveryManager.onPlatformServicesDiscovered(
                            status, connectionConfig.autoHandleServiceChanged());
                }
            });
        }

        @Override
        public void onServiceChanged() {
            runtime.state(() -> {
                if (gen == activeAttemptGen) {
                    serviceDiscoveryManager.onServiceChangedTriggered(
                            connectionConfig.autoHandleServiceChanged());
                }
            });
        }

        @Override
        public void onCharacteristicRead(@NonNull BleGattTarget target, @NonNull byte[] value, int status) {
            runtime.state(() -> {
                if (gen != activeAttemptGen) {
                    return;
                }
                if (status == 0) {
                    authRetryUsed = false;
                    operationQueue.succeedInFlightIfMatches(
                            YoBleGattOperationQueue.OpKind.READ_CHAR, value);
                } else {
                    operationQueue.failInFlightIfMatches(
                            YoBleGattOperationQueue.OpKind.READ_CHAR,
                            YoBleErrorMapper.fromGattStatus(
                                    status, BleErrorCode.READ_FAILED, MODULE, deviceId, target, "readCharacteristic"));
                }
            });
        }

        @Override
        public void onCharacteristicWrite(@NonNull BleGattTarget target, @Nullable byte[] echoedValue, int status) {
            runtime.state(() -> {
                if (gen != activeAttemptGen) {
                    return;
                }
                if (status == 0) {
                    authRetryUsed = false;
                    operationQueue.succeedInFlightIfMatches(
                            YoBleGattOperationQueue.OpKind.WRITE_CHAR, echoedValue);
                } else {
                    operationQueue.failInFlightIfMatches(
                            YoBleGattOperationQueue.OpKind.WRITE_CHAR,
                            YoBleErrorMapper.fromGattStatus(
                                    status, BleErrorCode.WRITE_FAILED, MODULE, deviceId, target, "writeCharacteristic"));
                }
            });
        }

        @Override
        public void onCharacteristicChanged(@NonNull BleGattTarget target, @NonNull byte[] value) {
            runtime.state(() -> {
                if (gen == activeAttemptGen) {
                    notificationRouter.dispatchCharacteristicChanged(target, value);
                }
            });
        }

        @Override
        public void onDescriptorRead(@NonNull BleGattTarget target, @NonNull byte[] value, int status) {
            runtime.state(() -> {
                if (gen != activeAttemptGen) {
                    return;
                }
                if (status == 0) {
                    authRetryUsed = false;
                    operationQueue.succeedInFlightIfMatches(
                            YoBleGattOperationQueue.OpKind.READ_DESC, value);
                } else {
                    operationQueue.failInFlightIfMatches(
                            YoBleGattOperationQueue.OpKind.READ_DESC,
                            YoBleErrorMapper.fromGattStatus(
                                    status, BleErrorCode.READ_FAILED, MODULE, deviceId, target, "readDescriptor"));
                }
            });
        }

        @Override
        public void onDescriptorWrite(@NonNull BleGattTarget target, int status) {
            runtime.state(() -> {
                if (gen != activeAttemptGen) {
                    return;
                }
                if (status == 0) {
                    authRetryUsed = false;
                    operationQueue.succeedInFlightIfMatches(
                            YoBleGattOperationQueue.OpKind.WRITE_DESC, null);
                } else {
                    BleErrorCode fallback = BleGattDatabaseSnapshot.CCCD_UUID.equals(target.descriptorUuid())
                            ? BleErrorCode.NOTIFY_FAILED
                            : BleErrorCode.WRITE_FAILED;
                    operationQueue.failInFlightIfMatches(
                            YoBleGattOperationQueue.OpKind.WRITE_DESC,
                            YoBleErrorMapper.fromGattStatus(
                                    status, fallback, MODULE, deviceId, target, "writeDescriptor"));
                }
            });
        }

        @Override
        public void onMtuChanged(int mtu, int status) {
            runtime.state(() -> {
                if (gen == activeAttemptGen) {
                    linkTuner.onPlatformMtuChanged(mtu, status);
                }
            });
        }

        @Override
        public void onPhyUpdate(@NonNull BlePhyConfig phy, int status) {
            runtime.state(() -> {
                if (gen == activeAttemptGen) {
                    linkTuner.onPlatformPhyUpdate(phy, status);
                }
            });
        }

        @Override
        public void onPhyRead(@NonNull BlePhyConfig phy, int status) {
            runtime.state(() -> {
                if (gen == activeAttemptGen) {
                    linkTuner.onPlatformPhyRead(phy, status);
                }
            });
        }

        @Override
        public void onConnectionUpdated(int interval, int latency, int timeout, int status) {
            runtime.state(() -> {
                if (gen == activeAttemptGen) {
                    linkTuner.onPlatformConnectionUpdated(interval, latency, timeout, status);
                }
            });
        }

        @Override
        public void onReadRemoteRssi(int rssi, int status) {
            runtime.state(() -> {
                if (gen == activeAttemptGen) {
                    linkTuner.onPlatformRssiRead(rssi, status);
                }
            });
        }

        @Override
        public void onReliableWriteCompleted(int status) {
            runtime.state(() -> {
                if (gen != activeAttemptGen) {
                    return;
                }
                YoBleGattOperationQueue.QueuedOperation active = operationQueue.currentInFlight();
                YoBleGattOperationQueue.OpKind targetKind =
                        active != null && active.kind() == YoBleGattOperationQueue.OpKind.RELIABLE_ABORT
                                ? YoBleGattOperationQueue.OpKind.RELIABLE_ABORT
                                : YoBleGattOperationQueue.OpKind.RELIABLE_EXECUTE;
                if (status == 0) {
                    operationQueue.succeedInFlightIfMatches(targetKind, null);
                } else {
                    operationQueue.failInFlightIfMatches(targetKind,
                            YoBleErrorMapper.fromGattStatus(status, BleErrorCode.WRITE_FAILED,
                                    MODULE, deviceId, null, "reliableWriteCompleted"));
                }
            });
        }
    }
}
