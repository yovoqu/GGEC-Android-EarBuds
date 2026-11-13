package com.yovo.lib_bluetooth.internal.ble.gatt;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.ble.error.YoBleErrorMapper;
import com.yovo.lib_bluetooth.internal.ble.gatt.YoBleGattOperationQueue.CompletionHandler;
import com.yovo.lib_bluetooth.internal.ble.gatt.YoBleGattOperationQueue.OpKind;
import com.yovo.lib_bluetooth.internal.ble.gatt.YoBleGattOperationQueue.StartResult;
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
 * 服务发现。缓存刷新作为队列中的 REFRESH_CACHE，完成后再发现；
 * 已配对设备在第一次发现前等待配置的延时。
 */
public final class YoBleServiceDiscoveryManager {

    private static final String MODULE = "ble-discovery";

    private static final class Waiter {
        @NonNull
        final String id;
        final boolean refresh;
        @NonNull
        final BleValueObserver<BleGattDatabaseSnapshot> observer;

        Waiter(@NonNull String id, boolean refresh, @NonNull BleValueObserver<BleGattDatabaseSnapshot> observer) {
            this.id = id;
            this.refresh = refresh;
            this.observer = observer;
        }
    }

    @NonNull
    private final YoBleSessionContext context;
    private final AtomicLong seq = new AtomicLong();
    @NonNull
    private final List<Waiter> waiters = new ArrayList<>();
    private boolean busy;
    private int epoch;
    @Nullable
    private String activeOpId;
    @Nullable
    private YoTimeoutHandle delayHandle;
    @Nullable
    private Runnable serviceChangedUnsubscribe;
    private boolean serviceChangedSubscribed;

    public YoBleServiceDiscoveryManager(@NonNull YoBleSessionContext context) {
        this.context = context;
    }

    @NonNull
    public BleOperationHandle discover(boolean forceRediscover,
                                       boolean refreshCache,
                                       @NonNull BleValueObserver<BleGattDatabaseSnapshot> observer) {
        context.runtime().checkStateThread();
        String opId = "ble-discover-" + seq.incrementAndGet();
        if (!context.isConnected()) {
            observer.onFailed(YoBleErrorMapper.failure(
                    BleErrorCode.CONNECTION_NOT_READY, MODULE, "not connected", context.deviceId()));
            return BleOperationHandle.completed(opId);
        }
        BleGattDatabaseSnapshot cached = context.currentDatabase();
        if (!forceRediscover && !refreshCache && cached != null) {
            observer.onSuccess(cached);
            return BleOperationHandle.completed(opId);
        }
        if (refreshCache && !context.bleConfig().allowHiddenApiCacheRefresh()) {
            observer.onFailed(YoBleErrorMapper.failure(
                    BleErrorCode.HIDDEN_API_BLOCKED, MODULE, "cache refresh is disabled", context.deviceId()));
            return BleOperationHandle.completed(opId);
        }
        waiters.add(new Waiter(opId, refreshCache, observer));
        if (!busy) {
            busy = true;
            long delay = context.bondState() == BleBondState.BONDED ? context.bondedDiscoveryDelayMillis() : 0L;
            int generation = epoch;
            delayHandle = context.runtime().stateDelayed(() -> enqueueDiscover(generation, refreshCache), delay);
        }
        return new BleOperationHandle(opId, () -> context.runtime().state(() -> cancelWaiter(opId)));
    }

    public void onPlatformServicesDiscovered(int status) {
        context.runtime().checkStateThread();
        activeOpId = null;
        // 平台发现回调必须先释放队列里的 DISCOVER_SERVICES 槽。
        // 槽还占着时，后面的 MTU / 命令通知排不进去，会话会一直停在发现完成之后。
        BleFailure discoveryFailure = status == 0 ? null : YoBleErrorMapper.fromGattStatus(
                status, BleErrorCode.SERVICE_DISCOVERY_FAILED, MODULE,
                context.deviceId(), null, "discoverServices");
        boolean released = context.operationQueue().complete(
                OpKind.DISCOVER_SERVICES, null, null, discoveryFailure);
        if (status != 0) {
            if (!released) {
                finish(discoveryFailure);
            }
            return;
        }
        if (!released) {
            return;
        }
        YoBleGattTransport transport = context.transport();
        if (transport == null) {
            finish(YoBleErrorMapper.failure(BleErrorCode.CONNECTION_NOT_READY, MODULE, "transport closed", context.deviceId()));
            return;
        }
        BleGattDatabaseSnapshot database = transport.captureDatabaseSnapshot(context.nowMillis());
        context.onDatabaseUpdated(database);
        context.snapshotStore().recordGattDatabase(database);
        subscribeServiceChanged(database);
        context.notifyConnectionObservers(observer -> observer.onServicesDiscovered(context.deviceId(), database));
        finishSuccess(database);
    }

    public void onServiceChanged() {
        context.runtime().checkStateThread();
        context.onDatabaseCleared();
        context.snapshotStore().clearGattDatabase(context.deviceId(), context.nowMillis());
        boolean refresh = context.bleConfig().allowHiddenApiCacheRefresh();
        discover(true, refresh, new BleValueObserver<BleGattDatabaseSnapshot>() {
            @Override
            public void onSuccess(@NonNull BleGattDatabaseSnapshot value) {
                context.notificationRouter().restoreSubscriptions(() -> { });
            }

            @Override
            public void onFailed(@NonNull BleFailure failure) {
                context.timeline().record(MODULE, "service changed rediscovery failed: " + failure.errorCode());
            }
        });
    }

    public void reset() {
        context.runtime().checkStateThread();
        epoch++;
        busy = false;
        activeOpId = null;
        if (delayHandle != null) {
            delayHandle.cancel();
            delayHandle = null;
        }
        if (serviceChangedUnsubscribe != null) {
            serviceChangedUnsubscribe.run();
            serviceChangedUnsubscribe = null;
        }
        serviceChangedSubscribed = false;
        BleFailure dropped = YoBleErrorMapper.failure(
                BleErrorCode.CONNECTION_NOT_READY, MODULE, "discovery reset", context.deviceId());
        for (Waiter waiter : new ArrayList<>(waiters)) {
            waiter.observer.onFailed(dropped);
        }
        waiters.clear();
    }

    private void enqueueDiscover(int generation, boolean refreshFirst) {
        if (generation != epoch) {
            return;
        }
        if (refreshFirst) {
            String refreshId = "ble-refresh-" + seq.incrementAndGet();
            activeOpId = refreshId;
            context.operationQueue().enqueue(refreshId, OpKind.REFRESH_CACHE, null, BleOperationPriority.HIGH,
                    context.operationTimeoutMillis(), this::startRefresh, new CompletionHandler() {
                        @Override
                        public void onSuccess(@Nullable Object payload) {
                            enqueueDiscover(generation, false);
                        }

                        @Override
                        public void onCancelled() {
                            finish(YoBleErrorMapper.failure(BleErrorCode.OPERATION_CANCELLED, MODULE,
                                    "cache refresh cancelled", context.deviceId()));
                        }

                        @Override
                        public void onFailed(@NonNull BleFailure failure) {
                            finish(failure);
                        }
                    });
            return;
        }
        String discoverId = "ble-disc-" + seq.incrementAndGet();
        activeOpId = discoverId;
        context.operationQueue().enqueue(discoverId, OpKind.DISCOVER_SERVICES, null, BleOperationPriority.HIGH,
                context.operationTimeoutMillis(), () -> {
                    YoBleGattTransport transport = context.transport();
                    if (transport == null || !transport.discoverServices()) {
                        return StartResult.failed(YoBleErrorMapper.failure(
                                BleErrorCode.SERVICE_DISCOVERY_FAILED, MODULE, "discoverServices rejected", context.deviceId()));
                    }
                    return StartResult.pending();
                }, new CompletionHandler() {
                    @Override
                    public void onCancelled() {
                        finish(YoBleErrorMapper.failure(BleErrorCode.OPERATION_CANCELLED, MODULE,
                                "discovery cancelled", context.deviceId()));
                    }

                    @Override
                    public void onFailed(@NonNull BleFailure failure) {
                        finish(failure);
                    }
                });
    }

    @NonNull
    private StartResult startRefresh() {
        YoBleGattTransport transport = context.transport();
        if (transport == null || !transport.refreshDeviceCache()) {
            return StartResult.failed(YoBleErrorMapper.failure(
                    BleErrorCode.HIDDEN_API_FAILED, MODULE, "refreshDeviceCache rejected", context.deviceId()));
        }
        context.runtime().stateDelayed(() -> context.operationQueue().complete(
                OpKind.REFRESH_CACHE, null, null, null), YoBleGattOperationQueue.CACHE_REFRESH_SETTLE_MS);
        return StartResult.pending();
    }

    private void subscribeServiceChanged(@NonNull BleGattDatabaseSnapshot database) {
        if (serviceChangedSubscribed || context.capabilityInspector().sdkInt() >= 31
                || !database.hasServiceChangedCharacteristic()) {
            return;
        }
        serviceChangedSubscribed = true;
        BleGattTarget target = BleGattTarget.characteristic(
                BleGattDatabaseSnapshot.GENERIC_ATTRIBUTE_SERVICE_UUID,
                BleGattDatabaseSnapshot.SERVICE_CHANGED_CHARACTERISTIC_UUID);
        serviceChangedUnsubscribe = context.notificationRouter().subscribeDuringSetup(
                target, BleNotificationSetupMode.COMPAT, new BleNotificationObserver() {
                    @Override
                    public void onCharacteristicChanged(@NonNull com.yovo.lib_bluetooth.model.ble.BleDeviceId deviceId,
                                                        @NonNull BleGattTarget changed,
                                                        @NonNull byte[] value) {
                        context.runtime().state(() -> onServiceChanged());
                    }
                });
    }

    private void cancelWaiter(@NonNull String opId) {
        Waiter removed = null;
        for (Waiter waiter : waiters) {
            if (waiter.id.equals(opId)) {
                removed = waiter;
                break;
            }
        }
        if (removed == null) {
            return;
        }
        waiters.remove(removed);
        removed.observer.onFailed(YoBleErrorMapper.failure(
                BleErrorCode.OPERATION_CANCELLED, MODULE, "discovery cancelled", context.deviceId()));
        if (waiters.isEmpty() && activeOpId != null) {
            context.operationQueue().cancel(activeOpId);
        }
    }

    private void finishSuccess(@NonNull BleGattDatabaseSnapshot database) {
        busy = false;
        activeOpId = null;
        List<Waiter> done = new ArrayList<>(waiters);
        waiters.clear();
        for (Waiter waiter : done) {
            waiter.observer.onSuccess(database);
        }
    }

    private void finish(@NonNull BleFailure failure) {
        busy = false;
        activeOpId = null;
        List<Waiter> done = new ArrayList<>(waiters);
        waiters.clear();
        for (Waiter waiter : done) {
            waiter.observer.onFailed(failure);
        }
    }
}
