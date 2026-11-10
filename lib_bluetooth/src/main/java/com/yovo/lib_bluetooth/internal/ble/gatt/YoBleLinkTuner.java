package com.yovo.lib_bluetooth.internal.ble.gatt;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.ble.error.YoBleErrorMapper;
import com.yovo.lib_bluetooth.internal.ble.gatt.YoBleGattOperationQueue.CompletionHandler;
import com.yovo.lib_bluetooth.internal.ble.gatt.YoBleGattOperationQueue.OpKind;
import com.yovo.lib_bluetooth.internal.ble.gatt.YoBleGattOperationQueue.StartResult;
import com.yovo.lib_bluetooth.internal.common.concurrency.YoTimeoutHandle;
import com.yovo.lib_bluetooth.model.ble.BleConnectionParameters;
import com.yovo.lib_bluetooth.model.ble.BleConnectionPriority;
import com.yovo.lib_bluetooth.model.ble.BleErrorCode;
import com.yovo.lib_bluetooth.model.ble.BleFailure;
import com.yovo.lib_bluetooth.model.ble.BleGattTarget;
import com.yovo.lib_bluetooth.model.ble.BleOperationHandle;
import com.yovo.lib_bluetooth.model.ble.BleOperationPriority;
import com.yovo.lib_bluetooth.model.ble.BlePhy;
import com.yovo.lib_bluetooth.model.ble.BlePhyConfig;
import com.yovo.lib_bluetooth.model.ble.BlePhyOptions;
import com.yovo.lib_bluetooth.model.ble.BleValueObserver;
import com.yovo.lib_bluetooth.model.common.Result;

import java.util.concurrent.atomic.AtomicLong;

/**
 * MTU、PHY、连接优先级和 RSSI。五类请求共用一条入队路径。
 */
public final class YoBleLinkTuner {

    private static final String MODULE = "ble-link";
    public static final long CONNECTION_PRIORITY_FALLBACK_MS = 200L;
    public static final long ANDROID_13_PHY_FALLBACK_MS = 1_000L;

    @NonNull
    private final YoBleSessionContext context;
    private final AtomicLong seq = new AtomicLong();
    @Nullable
    private YoTimeoutHandle priorityFallback;
    @Nullable
    private YoTimeoutHandle phyFallback;

    public YoBleLinkTuner(@NonNull YoBleSessionContext context) {
        this.context = context;
    }

    @NonNull
    public BleOperationHandle requestMtu(int requested, @NonNull BleValueObserver<Integer> observer) {
        int mtu = Math.max(23, Math.min(517, requested));
        return submit("mtu", OpKind.REQUEST_MTU, BleOperationPriority.HIGH, () -> {
            YoBleGattTransport transport = context.transport();
            if (transport == null || !transport.requestMtu(mtu)) {
                return StartResult.failed(rejected("requestMtu"));
            }
            return StartResult.pending();
        }, observer);
    }

    @NonNull
    public Result<Integer> overrideMtu(int requested) {
        int mtu = Math.max(23, Math.min(517, requested));
        if (!context.isConnected()) {
            return Result.failure(YoBleErrorMapper.failure(
                    BleErrorCode.CONNECTION_NOT_READY, MODULE, "not connected", context.deviceId()));
        }
        context.runtime().state(() -> context.onMtuOverridden(mtu));
        return Result.success(mtu);
    }

    @NonNull
    public BleOperationHandle requestConnectionPriority(@NonNull BleConnectionPriority priority,
                                                        @NonNull BleValueObserver<Void> observer) {
        BleOperationHandle handle = submit("priority", OpKind.CONNECTION_PRIORITY, BleOperationPriority.NORMAL, () -> {
            YoBleGattTransport transport = context.transport();
            if (transport == null || !transport.requestConnectionPriority(priority)) {
                return StartResult.failed(rejected("requestConnectionPriority"));
            }
            priorityFallback = context.runtime().stateDelayed(() -> context.operationQueue().complete(
                    OpKind.CONNECTION_PRIORITY, null, null, null), CONNECTION_PRIORITY_FALLBACK_MS);
            return StartResult.pending();
        }, observer);
        return handle;
    }

    @NonNull
    public BleOperationHandle setPreferredPhy(@NonNull BlePhy tx,
                                              @NonNull BlePhy rx,
                                              @NonNull BlePhyOptions options,
                                              @NonNull BleValueObserver<BlePhyConfig> observer) {
        if (context.capabilityInspector().sdkInt() < 26) {
            String opId = "ble-phy-unsupported";
            observer.onFailed(YoBleErrorMapper.failure(
                    BleErrorCode.UNSUPPORTED_OPERATION, MODULE, "PHY requires API 26", context.deviceId()));
            return BleOperationHandle.completed(opId);
        }
        return submit("phy", OpKind.SET_PHY, BleOperationPriority.NORMAL, () -> {
            YoBleGattTransport transport = context.transport();
            if (transport == null || !transport.setPreferredPhy(tx, rx, options)) {
                return StartResult.failed(rejected("setPreferredPhy"));
            }
            if (context.capabilityInspector().sdkInt() == 33) {
                phyFallback = context.runtime().stateDelayed(() -> {
                    YoBleGattTransport current = context.transport();
                    if (current != null) {
                        current.readPhy();
                    }
                }, ANDROID_13_PHY_FALLBACK_MS);
            }
            return StartResult.pending();
        }, observer);
    }

    @NonNull
    public BleOperationHandle readPhy(@NonNull BleValueObserver<BlePhyConfig> observer) {
        return submit("read-phy", OpKind.READ_PHY, BleOperationPriority.NORMAL, () -> {
            YoBleGattTransport transport = context.transport();
            if (transport == null || !transport.readPhy()) {
                return StartResult.failed(rejected("readPhy"));
            }
            return StartResult.pending();
        }, observer);
    }

    @NonNull
    public BleOperationHandle readRemoteRssi(@NonNull BleValueObserver<Integer> observer) {
        return submit("rssi", OpKind.READ_RSSI, BleOperationPriority.LOW, () -> {
            YoBleGattTransport transport = context.transport();
            if (transport == null || !transport.readRemoteRssi()) {
                return StartResult.failed(rejected("readRemoteRssi"));
            }
            return StartResult.pending();
        }, observer);
    }

    public void onPlatformMtuChanged(int mtu, int status) {
        if (status == 0) {
            context.onMtuUpdated(mtu);
            context.notifyConnectionObservers(observer -> observer.onMtuChanged(context.deviceId(), mtu));
        }
        complete(OpKind.REQUEST_MTU, status, mtu, "requestMtu");
    }

    public void onPlatformConnectionUpdated(int interval, int latency, int timeout, int status) {
        cancel(priorityFallback);
        priorityFallback = null;
        if (status == 0) {
            BleConnectionParameters parameters = new BleConnectionParameters(interval, latency, timeout);
            context.onConnectionParametersUpdated(parameters);
            context.notifyConnectionObservers(observer -> observer.onConnectionUpdated(context.deviceId(), parameters));
        }
        complete(OpKind.CONNECTION_PRIORITY, status, null, "requestConnectionPriority");
    }

    public void onPlatformPhyUpdate(@NonNull BlePhyConfig phy, int status) {
        cancel(phyFallback);
        phyFallback = null;
        if (status == 0) {
            context.onPhyUpdated(phy);
            context.notifyConnectionObservers(observer -> observer.onPhyUpdated(context.deviceId(), phy));
        }
        complete(OpKind.SET_PHY, status, phy, "setPreferredPhy");
    }

    public void onPlatformPhyRead(@NonNull BlePhyConfig phy, int status) {
        if (status == 0) {
            context.onPhyUpdated(phy);
            context.notifyConnectionObservers(observer -> observer.onPhyUpdated(context.deviceId(), phy));
        }
        if (!context.operationQueue().complete(OpKind.READ_PHY, null, status == 0 ? phy : null,
                status == 0 ? null : failure(status, "readPhy"))) {
            context.operationQueue().complete(OpKind.SET_PHY, null, status == 0 ? phy : null,
                    status == 0 ? null : failure(status, "setPreferredPhy"));
        }
    }

    public void onPlatformRssiRead(int rssi, int status) {
        if (status == 0) {
            context.onRssiUpdated(rssi);
            context.notifyConnectionObservers(observer -> observer.onRssiRead(context.deviceId(), rssi));
        }
        complete(OpKind.READ_RSSI, status, rssi, "readRemoteRssi");
    }

    public void cancelAllFallbacks() {
        cancel(priorityFallback);
        cancel(phyFallback);
        priorityFallback = null;
        phyFallback = null;
    }

    @NonNull
    private <T> BleOperationHandle submit(@NonNull String name,
                                          @NonNull OpKind kind,
                                          @NonNull BleOperationPriority priority,
                                          @NonNull YoBleGattOperationQueue.TaskExecutor executor,
                                          @NonNull BleValueObserver<T> observer) {
        String opId = "ble-" + name + "-" + seq.incrementAndGet();
        if (!context.isReady() && !context.isConnected()) {
            observer.onFailed(YoBleErrorMapper.failure(
                    BleErrorCode.CONNECTION_NOT_READY, MODULE, "not connected", context.deviceId()));
            return BleOperationHandle.completed(opId);
        }
        context.operationQueue().enqueue(opId, kind, null, priority, context.operationTimeoutMillis(), executor,
                new CompletionHandler() {
                    @Override
                    public void onSuccess(@Nullable Object payload) {
                        @SuppressWarnings("unchecked")
                        T value = (T) payload;
                        observer.onSuccess(value);
                    }

                    @Override
                    public void onCancelled() {
                        observer.onFailed(YoBleErrorMapper.failure(
                                BleErrorCode.OPERATION_CANCELLED, MODULE, name + " cancelled", context.deviceId()));
                    }

                    @Override
                    public void onFailed(@NonNull BleFailure failure) {
                        observer.onFailed(failure);
                    }
                });
        return new BleOperationHandle(opId, () -> context.runtime().state(
                () -> context.operationQueue().cancel(opId)));
    }

    private void complete(@NonNull OpKind kind, int status, @Nullable Object payload, @NonNull String label) {
        context.operationQueue().complete(kind, null, status == 0 ? payload : null,
                status == 0 ? null : failure(status, label));
    }

    @NonNull
    private BleFailure failure(int status, @NonNull String label) {
        return YoBleErrorMapper.fromGattStatus(status, BleErrorCode.REQUEST_NOT_SUPPORTED, MODULE,
                context.deviceId(), (BleGattTarget) null, label);
    }

    @NonNull
    private BleFailure rejected(@NonNull String label) {
        return YoBleErrorMapper.failure(BleErrorCode.REQUEST_NOT_SUPPORTED, MODULE,
                label + " rejected", context.deviceId());
    }

    private static void cancel(@Nullable YoTimeoutHandle handle) {
        if (handle != null) {
            handle.cancel();
        }
    }
}
