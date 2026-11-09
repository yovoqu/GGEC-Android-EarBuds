package com.yovo.lib_bluetooth.internal.ble.gatt;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.ble.error.YoBleErrorMapper;
import com.yovo.lib_bluetooth.internal.common.concurrency.YoTimeoutHandle;
import com.yovo.lib_bluetooth.model.ble.BleBondState;
import com.yovo.lib_bluetooth.model.ble.BleErrorCode;
import com.yovo.lib_bluetooth.model.ble.BleFailure;
import com.yovo.lib_bluetooth.model.ble.BleGattDatabaseSnapshot;
import com.yovo.lib_bluetooth.model.ble.BleGattTarget;
import com.yovo.lib_bluetooth.model.ble.BleNotificationObserver;
import com.yovo.lib_bluetooth.model.ble.BleNotificationSetupMode;
import com.yovo.lib_bluetooth.model.ble.BleOperationHandle;
import com.yovo.lib_bluetooth.model.ble.BleOperationPriority;
import com.yovo.lib_bluetooth.model.ble.BleValueObserver;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * GATT 服务发现管理器：
 * <ul>
 *   <li>已配对设备保留可配置延时（规避系统秘钥协商与发现服务冲突）；</li>
 *   <li>支持受控缓存刷新（{@code refreshDeviceCache}）；</li>
 *   <li>支持 Service Changed (0x1801/0x2A05 on API 24-30 及 API 31+ {@code onServiceChanged}) 自动重发现；</li>
 *   <li>构建不可变 {@link BleGattDatabaseSnapshot}。</li>
 * </ul>
 */
public final class YoBleServiceDiscoveryManager {

    private static final String MODULE = "ble-discovery";

    private static final class Waiter {
        final long id;
        @NonNull
        final BleValueObserver<BleGattDatabaseSnapshot> observer;

        Waiter(long id, @NonNull BleValueObserver<BleGattDatabaseSnapshot> observer) {
            this.id = id;
            this.observer = observer;
        }
    }

    private final YoBleSessionContext context;
    private final AtomicLong discoverySeq = new AtomicLong();
    private final AtomicLong waiterSeq = new AtomicLong();
    private final List<Waiter> pendingWaiters = new ArrayList<>();
    private long activeDiscoveryEpoch = 0L;
    private boolean discoveryInProgress = false;
    private boolean rediscoveryQueued = false;
    private boolean serviceChangedSubscribed = false;
    @Nullable
    private YoTimeoutHandle bondedDelayHandle;
    @Nullable
    private String activeQueueOpId;
    @Nullable
    private Runnable serviceChangedUnsubscribe;

    public YoBleServiceDiscoveryManager(@NonNull YoBleSessionContext context) {
        this.context = context;
    }

    @NonNull
    public synchronized BleOperationHandle discoverServices(boolean forceRefresh,
                                                            boolean autoHandleServiceChanged,
                                                            @NonNull BleValueObserver<BleGattDatabaseSnapshot> observer) {
        long waiterId = waiterSeq.incrementAndGet();
        String handleId = "ble-discover-" + waiterId;
        if (!context.isConnected()) {
            BleFailure failure = YoBleErrorMapper.failure(
                    BleErrorCode.CONNECTION_NOT_READY, MODULE,
                    "BLE device is not connected", context.deviceId());
            context.runtime().callback(() -> observer.onFailed(failure));
            return BleOperationHandle.completed(handleId);
        }

        if (!forceRefresh && context.currentDatabase() != null && !discoveryInProgress) {
            BleGattDatabaseSnapshot cached = context.currentDatabase();
            context.runtime().callback(() -> observer.onSuccess(cached));
            return BleOperationHandle.completed(handleId);
        }

        pendingWaiters.add(new Waiter(waiterId, observer));
        BleOperationHandle handle = new BleOperationHandle(
                handleId, () -> context.runtime().state(() -> cancelWaiter(waiterId)));

        if (discoveryInProgress) {
            return handle;
        }
        discoveryInProgress = true;
        final long epoch = ++activeDiscoveryEpoch;

        if (forceRefresh) {
            YoBleGattTransport transport = context.transport();
            if (transport != null && context.bleConfig().allowHiddenApiCacheRefresh()) {
                boolean refreshed = transport.refreshDeviceCache();
                context.timeline().record(MODULE, "refreshDeviceCache=" + refreshed);
            }
        }

        long delayMs = context.bondState() == BleBondState.BONDED
                ? context.bondedDiscoveryDelayMillis() : 0L;
        if (delayMs > 0L) {
            if (bondedDelayHandle != null) {
                bondedDelayHandle.cancel();
            }
            context.timeline().record(MODULE,
                    "delaying service discovery " + delayMs + "ms for bonded device");
            bondedDelayHandle = context.runtime().scheduler().schedule(
                    () -> context.runtime().state(() -> enqueueDiscover(epoch, autoHandleServiceChanged)),
                    delayMs);
        } else {
            enqueueDiscover(epoch, autoHandleServiceChanged);
        }
        return handle;
    }

    public void onPlatformServicesDiscovered(int rawStatus, boolean autoHandleServiceChanged) {
        YoBleGattOperationQueue.QueuedOperation op =
                context.operationQueue().completeInFlightIfMatches(
                        YoBleGattOperationQueue.OpKind.DISCOVER_SERVICES);
        if (op == null) {
            return;
        }
        synchronized (this) {
            activeQueueOpId = null;
        }
        if (rawStatus != 0) {
            BleFailure failure = YoBleErrorMapper.fromGattStatus(
                    rawStatus, BleErrorCode.SERVICE_DISCOVERY_FAILED, MODULE,
                    context.deviceId(), null, "discoverServices");
            completeFailure(failure);
            return;
        }

        YoBleGattTransport transport = context.transport();
        BleGattDatabaseSnapshot db = transport != null
                ? transport.captureDatabaseSnapshot(context.nowMillis())
                : new BleGattDatabaseSnapshot(
                        context.deviceId(), new ArrayList<>(), context.nowMillis());

        context.onDatabaseUpdated(db);
        context.snapshotStore().recordGattDatabase(db);
        context.timeline().record(MODULE, "services discovered count=" + db.services().size());

        // 若存在 0x1801/0x2A05 且 API<31（或通用开启），订阅 Service Changed Indication
        boolean shouldSubscribeSc = false;
        synchronized (this) {
            if (autoHandleServiceChanged && db.hasServiceChangedCharacteristic()
                    && !serviceChangedSubscribed && serviceChangedUnsubscribe == null
                    && context.capabilityInspector().sdkInt() < 31) {
                serviceChangedSubscribed = true;
                shouldSubscribeSc = true;
            }
        }
        if (shouldSubscribeSc) {
            BleGattTarget scTarget = BleGattTarget.characteristic(
                    BleGattDatabaseSnapshot.GENERIC_ATTRIBUTE_SERVICE_UUID,
                    BleGattDatabaseSnapshot.SERVICE_CHANGED_CHARACTERISTIC_UUID);
            Runnable unsub = context.notificationRouter().subscribeDuringSetup(scTarget, BleNotificationSetupMode.COMPAT,
                    new BleNotificationObserver() {
                        @Override
                        public void onCharacteristicChanged(@NonNull com.yovo.lib_bluetooth.model.ble.BleDeviceId deviceId,
                                                            @NonNull BleGattTarget target,
                                                            @NonNull byte[] value) {
                            onServiceChangedTriggered(autoHandleServiceChanged);
                        }

                        @Override
                        public void onNotificationFailed(@NonNull BleFailure failure) {
                            synchronized (YoBleServiceDiscoveryManager.this) {
                                serviceChangedSubscribed = false;
                                serviceChangedUnsubscribe = null;
                            }
                        }
                    });
            synchronized (this) {
                if (serviceChangedSubscribed) {
                    serviceChangedUnsubscribe = unsub;
                } else {
                    unsub.run();
                }
            }
        }

        completeSuccess(db);
        context.notifyConnectionObservers(obs -> obs.onServicesDiscovered(context.deviceId(), db));

        boolean triggerQueuedRediscovery;
        synchronized (this) {
            triggerQueuedRediscovery = rediscoveryQueued;
            if (triggerQueuedRediscovery) {
                rediscoveryQueued = false;
            }
        }
        if (triggerQueuedRediscovery) {
            context.onDatabaseCleared();
            context.snapshotStore().clearGattDatabase(context.deviceId(), context.nowMillis());
            discoverServices(true, autoHandleServiceChanged, new BleValueObserver<BleGattDatabaseSnapshot>() {
                @Override
                public void onSuccess(@NonNull BleGattDatabaseSnapshot value) {
                }
            });
        }
    }

    public synchronized void onServiceChangedTriggered(boolean autoHandleServiceChanged) {
        if (!autoHandleServiceChanged || !context.isConnected()) {
            return;
        }
        context.timeline().record(MODULE, "ServiceChanged received -> refreshing GATT database");
        if (discoveryInProgress) {
            rediscoveryQueued = true;
            return;
        }
        context.onDatabaseCleared();
        context.snapshotStore().clearGattDatabase(context.deviceId(), context.nowMillis());
        discoverServices(true, true, new BleValueObserver<BleGattDatabaseSnapshot>() {
            @Override
            public void onSuccess(@NonNull BleGattDatabaseSnapshot value) {
            }
        });
    }

    public synchronized void reset() {
        activeDiscoveryEpoch++;
        discoveryInProgress = false;
        rediscoveryQueued = false;
        serviceChangedSubscribed = false;
        if (bondedDelayHandle != null) {
            bondedDelayHandle.cancel();
            bondedDelayHandle = null;
        }
        String qOpId = activeQueueOpId;
        activeQueueOpId = null;
        if (qOpId != null) {
            context.operationQueue().cancel(qOpId);
        }
        if (serviceChangedUnsubscribe != null) {
            serviceChangedUnsubscribe.run();
            serviceChangedUnsubscribe = null;
        }
        pendingWaiters.clear();
    }

    private void cancelWaiter(long waiterId) {
        Waiter cancelledWaiter = null;
        String queueOpToCancel = null;
        synchronized (this) {
            for (int i = 0; i < pendingWaiters.size(); i++) {
                if (pendingWaiters.get(i).id == waiterId) {
                    cancelledWaiter = pendingWaiters.remove(i);
                    break;
                }
            }
            if (cancelledWaiter == null) {
                return;
            }
            if (pendingWaiters.isEmpty() && discoveryInProgress) {
                discoveryInProgress = false;
                rediscoveryQueued = false;
                activeDiscoveryEpoch++;
                if (bondedDelayHandle != null) {
                    bondedDelayHandle.cancel();
                    bondedDelayHandle = null;
                }
                queueOpToCancel = activeQueueOpId;
                activeQueueOpId = null;
            }
        }
        BleFailure cancelFailure = YoBleErrorMapper.failure(
                BleErrorCode.OPERATION_CANCELLED, MODULE,
                "Service discovery cancelled", context.deviceId());
        BleValueObserver<BleGattDatabaseSnapshot> obs = cancelledWaiter.observer;
        context.runtime().callback(() -> obs.onFailed(cancelFailure));
        if (queueOpToCancel != null) {
            context.operationQueue().cancel(queueOpToCancel);
        }
    }

    private void enqueueDiscover(long epoch, boolean autoHandleServiceChanged) {
        synchronized (this) {
            if (!discoveryInProgress || epoch != activeDiscoveryEpoch) {
                return;
            }
            bondedDelayHandle = null;
        }
        if (!context.isConnected()) {
            completeFailure(YoBleErrorMapper.failure(
                    BleErrorCode.CONNECTION_NOT_READY, MODULE,
                    "Connection closed before discoverServices executed", context.deviceId()));
            return;
        }
        String qOpId = "discover-" + discoverySeq.incrementAndGet();
        synchronized (this) {
            if (!discoveryInProgress || epoch != activeDiscoveryEpoch) {
                return;
            }
            activeQueueOpId = qOpId;
        }
        context.operationQueue().enqueue(
                qOpId,
                YoBleGattOperationQueue.OpKind.DISCOVER_SERVICES,
                null,
                BleOperationPriority.HIGH,
                context.operationTimeoutMillis(),
                () -> {
                    YoBleGattTransport transport = context.transport();
                    if (transport == null || !transport.discoverServices()) {
                        return YoBleErrorMapper.failure(
                                BleErrorCode.SERVICE_DISCOVERY_FAILED, MODULE,
                                "BluetoothGatt.discoverServices() returned false",
                                context.deviceId());
                    }
                    return null;
                },
                new YoBleGattOperationQueue.CompletionHandler() {
                    @Override
                    public void onCancelled() {
                        synchronized (YoBleServiceDiscoveryManager.this) {
                            if (epoch != activeDiscoveryEpoch) {
                                return;
                            }
                            activeQueueOpId = null;
                        }
                        completeFailure(YoBleErrorMapper.failure(
                                BleErrorCode.OPERATION_CANCELLED, MODULE,
                                "Service discovery cancelled", context.deviceId()));
                    }

                    @Override
                    public void onFailed(@NonNull BleFailure failure) {
                        synchronized (YoBleServiceDiscoveryManager.this) {
                            if (epoch != activeDiscoveryEpoch) {
                                return;
                            }
                            activeQueueOpId = null;
                        }
                        completeFailure(failure);
                    }
                });
    }

    private void completeSuccess(@NonNull BleGattDatabaseSnapshot snapshot) {
        List<Waiter> waiters;
        synchronized (this) {
            discoveryInProgress = false;
            waiters = new ArrayList<>(pendingWaiters);
            pendingWaiters.clear();
        }
        for (Waiter waiter : waiters) {
            context.runtime().callback(() -> waiter.observer.onSuccess(snapshot));
        }
    }

    private void completeFailure(@NonNull BleFailure failure) {
        List<Waiter> waiters;
        synchronized (this) {
            discoveryInProgress = false;
            waiters = new ArrayList<>(pendingWaiters);
            pendingWaiters.clear();
        }
        for (Waiter waiter : waiters) {
            context.runtime().callback(() -> waiter.observer.onFailed(failure));
        }
    }
}
