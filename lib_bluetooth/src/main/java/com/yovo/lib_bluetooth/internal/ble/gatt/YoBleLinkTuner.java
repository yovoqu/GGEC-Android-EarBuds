package com.yovo.lib_bluetooth.internal.ble.gatt;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.ble.error.YoBleErrorMapper;
import com.yovo.lib_bluetooth.internal.common.concurrency.YoTimeoutHandle;
import com.yovo.lib_bluetooth.model.ble.BleConnectionParameters;
import com.yovo.lib_bluetooth.model.ble.BleConnectionPriority;
import com.yovo.lib_bluetooth.model.ble.BleErrorCode;
import com.yovo.lib_bluetooth.model.ble.BleFailure;
import com.yovo.lib_bluetooth.model.ble.BleOperationHandle;
import com.yovo.lib_bluetooth.model.ble.BleOperationPriority;
import com.yovo.lib_bluetooth.model.ble.BlePhy;
import com.yovo.lib_bluetooth.model.ble.BlePhyConfig;
import com.yovo.lib_bluetooth.model.ble.BlePhyOptions;
import com.yovo.lib_bluetooth.model.ble.BleValueObserver;
import com.yovo.lib_bluetooth.model.common.Result;

import java.util.concurrent.atomic.AtomicLong;

/**
 * BLE 链路调优协调器：
 * <ul>
 *   <li>MTU 协商与本地 Override；</li>
 *   <li>ConnectionPriority 请求及 200ms 无回调兼容兜底；</li>
 *   <li>PHY 读写及 Android 13 (API 33) 缺失 {@code onPhyUpdate} 1000ms 自动 {@code readPhy()} 兜底；</li>
 *   <li>Remote RSSI 读取。</li>
 * </ul>
 */
public final class YoBleLinkTuner {

    public static final long CONNECTION_PRIORITY_FALLBACK_MS = 200L;
    public static final long ANDROID_13_PHY_FALLBACK_MS = 1_000L;

    private static final String MODULE = "ble-link-tuner";

    private final YoBleSessionContext context;
    private final AtomicLong seq = new AtomicLong();

    @Nullable
    private YoTimeoutHandle priorityFallbackHandle;
    @Nullable
    private YoTimeoutHandle phyFallbackHandle;

    public YoBleLinkTuner(@NonNull YoBleSessionContext context) {
        this.context = context;
    }

    @NonNull
    public BleOperationHandle requestMtu(int requestedMtu,
                                         @NonNull BleValueObserver<Integer> observer) {
        String opId = "ble-mtu-" + seq.incrementAndGet();
        int clamped = Math.max(23, Math.min(517, requestedMtu));

        if (!context.isConnected()) {
            BleFailure f = YoBleErrorMapper.failure(
                    BleErrorCode.CONNECTION_NOT_READY, MODULE,
                    "BLE device is not connected", context.deviceId());
            context.runtime().callback(() -> observer.onFailed(f));
            return BleOperationHandle.completed(opId);
        }

        context.operationQueue().enqueue(
                opId,
                YoBleGattOperationQueue.OpKind.REQUEST_MTU,
                null,
                BleOperationPriority.HIGH,
                context.operationTimeoutMillis(),
                () -> {
                    YoBleGattTransport transport = context.transport();
                    if (transport == null || !transport.requestMtu(clamped)) {
                        return YoBleErrorMapper.failure(
                                BleErrorCode.REQUEST_NOT_SUPPORTED, MODULE,
                                "BluetoothGatt.requestMtu(" + clamped + ") returned false",
                                context.deviceId());
                    }
                    context.timeline().record(MODULE, "requestMtu=" + clamped);
                    return null;
                },
                new YoBleGattOperationQueue.CompletionHandler() {
                    @Override
                    public void onSuccess(@Nullable Object payload) {
                        int negotiated = payload instanceof Integer ? (Integer) payload : context.currentMtu();
                        context.runtime().callback(() -> observer.onSuccess(negotiated));
                    }

                    @Override
                    public void onCancelled() {
                        context.runtime().callback(() -> observer.onFailed(YoBleErrorMapper.failure(
                                BleErrorCode.OPERATION_CANCELLED, MODULE,
                                "requestMtu cancelled", context.deviceId())));
                    }

                    @Override
                    public void onFailed(@NonNull BleFailure failure) {
                        context.runtime().callback(() -> observer.onFailed(failure));
                    }
                });

        return new BleOperationHandle(opId, () -> context.operationQueue().cancel(opId));
    }

    @NonNull
    public Result<Integer> overrideMtu(int mtu) {
        int clamped = Math.max(23, Math.min(517, mtu));
        context.onMtuOverridden(clamped);
        context.timeline().record(MODULE, "overrideMtu=" + clamped);
        return Result.success(clamped);
    }

    @NonNull
    public BleOperationHandle requestConnectionPriority(@NonNull BleConnectionPriority priority,
                                                        @NonNull BleValueObserver<Void> observer) {
        String opId = "ble-conn-prio-" + seq.incrementAndGet();
        if (!context.isReady() && !context.isConnected()) {
            BleFailure f = YoBleErrorMapper.failure(
                    BleErrorCode.CONNECTION_NOT_READY, MODULE,
                    "BLE device is not connected", context.deviceId());
            context.runtime().callback(() -> observer.onFailed(f));
            return BleOperationHandle.completed(opId);
        }

        context.operationQueue().enqueue(
                opId,
                YoBleGattOperationQueue.OpKind.CONNECTION_PRIORITY,
                null,
                BleOperationPriority.NORMAL,
                context.operationTimeoutMillis(),
                () -> {
                    YoBleGattTransport transport = context.transport();
                    if (transport == null || !transport.requestConnectionPriority(priority)) {
                        return YoBleErrorMapper.failure(
                                BleErrorCode.REQUEST_NOT_SUPPORTED, MODULE,
                                "requestConnectionPriority returned false", context.deviceId());
                    }
                    context.timeline().record(MODULE, "requestConnectionPriority=" + priority);
                    armConnectionPriorityFallback();
                    return null;
                },
                new YoBleGattOperationQueue.CompletionHandler() {
                    @Override
                    public void onSuccess(@Nullable Object payload) {
                        cancelPriorityFallback();
                        context.runtime().callback(() -> observer.onSuccess(null));
                    }

                    @Override
                    public void onCancelled() {
                        cancelPriorityFallback();
                        context.runtime().callback(() -> observer.onFailed(YoBleErrorMapper.failure(
                                BleErrorCode.OPERATION_CANCELLED, MODULE,
                                "requestConnectionPriority cancelled", context.deviceId())));
                    }

                    @Override
                    public void onFailed(@NonNull BleFailure failure) {
                        cancelPriorityFallback();
                        context.runtime().callback(() -> observer.onFailed(failure));
                    }
                });

        return new BleOperationHandle(opId, () -> context.operationQueue().cancel(opId));
    }

    @NonNull
    public BleOperationHandle setPreferredPhy(@NonNull BlePhy txPhy,
                                              @NonNull BlePhy rxPhy,
                                              @NonNull BlePhyOptions options,
                                              @NonNull BleValueObserver<BlePhyConfig> observer) {
        String opId = "ble-set-phy-" + seq.incrementAndGet();
        if (context.capabilityInspector().sdkInt() < 26) {
            BleFailure f = YoBleErrorMapper.failure(
                    BleErrorCode.UNSUPPORTED_OPERATION, MODULE,
                    "LE PHY control requires Android 8.0 (API 26+)", context.deviceId());
            context.runtime().callback(() -> observer.onFailed(f));
            return BleOperationHandle.completed(opId);
        }
        if (!context.isConnected()) {
            BleFailure f = YoBleErrorMapper.failure(
                    BleErrorCode.CONNECTION_NOT_READY, MODULE,
                    "BLE device is not connected", context.deviceId());
            context.runtime().callback(() -> observer.onFailed(f));
            return BleOperationHandle.completed(opId);
        }

        context.operationQueue().enqueue(
                opId,
                YoBleGattOperationQueue.OpKind.SET_PHY,
                null,
                BleOperationPriority.NORMAL,
                context.operationTimeoutMillis(),
                () -> {
                    YoBleGattTransport transport = context.transport();
                    if (transport == null || !transport.setPreferredPhy(txPhy, rxPhy, options)) {
                        return YoBleErrorMapper.failure(
                                BleErrorCode.UNSUPPORTED_OPERATION, MODULE,
                                "setPreferredPhy returned false", context.deviceId());
                    }
                    context.timeline().record(MODULE, "setPreferredPhy tx=" + txPhy + " rx=" + rxPhy);
                    if (context.capabilityInspector().sdkInt() == 33) {
                        // Android 13 知名缺陷：setPreferredPhy 常不触发 onPhyUpdate -> 1000ms 兜底 readPhy()
                        armAndroid13PhyFallback(opId);
                    }
                    return null;
                },
                new YoBleGattOperationQueue.CompletionHandler() {
                    @Override
                    public void onSuccess(@Nullable Object payload) {
                        cancelPhyFallback();
                        BlePhyConfig cfg = payload instanceof BlePhyConfig
                                ? (BlePhyConfig) payload
                                : new BlePhyConfig(txPhy, rxPhy, options);
                        context.runtime().callback(() -> observer.onSuccess(cfg));
                    }

                    @Override
                    public void onCancelled() {
                        cancelPhyFallback();
                        context.runtime().callback(() -> observer.onFailed(YoBleErrorMapper.failure(
                                BleErrorCode.OPERATION_CANCELLED, MODULE,
                                "setPreferredPhy cancelled", context.deviceId())));
                    }

                    @Override
                    public void onFailed(@NonNull BleFailure failure) {
                        cancelPhyFallback();
                        context.runtime().callback(() -> observer.onFailed(failure));
                    }
                });

        return new BleOperationHandle(opId, () -> context.operationQueue().cancel(opId));
    }

    @NonNull
    public BleOperationHandle readPhy(@NonNull BleValueObserver<BlePhyConfig> observer) {
        String opId = "ble-read-phy-" + seq.incrementAndGet();
        if (context.capabilityInspector().sdkInt() < 26) {
            BleFailure f = YoBleErrorMapper.failure(
                    BleErrorCode.UNSUPPORTED_OPERATION, MODULE,
                    "LE PHY control requires Android 8.0 (API 26+)", context.deviceId());
            context.runtime().callback(() -> observer.onFailed(f));
            return BleOperationHandle.completed(opId);
        }
        if (!context.isConnected()) {
            BleFailure f = YoBleErrorMapper.failure(
                    BleErrorCode.CONNECTION_NOT_READY, MODULE,
                    "BLE device is not connected", context.deviceId());
            context.runtime().callback(() -> observer.onFailed(f));
            return BleOperationHandle.completed(opId);
        }

        context.operationQueue().enqueue(
                opId,
                YoBleGattOperationQueue.OpKind.READ_PHY,
                null,
                BleOperationPriority.NORMAL,
                context.operationTimeoutMillis(),
                () -> {
                    YoBleGattTransport transport = context.transport();
                    if (transport == null || !transport.readPhy()) {
                        return YoBleErrorMapper.failure(
                                BleErrorCode.UNSUPPORTED_OPERATION, MODULE,
                                "readPhy returned false", context.deviceId());
                    }
                    return null;
                },
                new YoBleGattOperationQueue.CompletionHandler() {
                    @Override
                    public void onSuccess(@Nullable Object payload) {
                        BlePhyConfig cfg = payload instanceof BlePhyConfig
                                ? (BlePhyConfig) payload : BlePhyConfig.le1m();
                        context.runtime().callback(() -> observer.onSuccess(cfg));
                    }

                    @Override
                    public void onCancelled() {
                        context.runtime().callback(() -> observer.onFailed(YoBleErrorMapper.failure(
                                BleErrorCode.OPERATION_CANCELLED, MODULE,
                                "readPhy cancelled", context.deviceId())));
                    }

                    @Override
                    public void onFailed(@NonNull BleFailure failure) {
                        context.runtime().callback(() -> observer.onFailed(failure));
                    }
                });

        return new BleOperationHandle(opId, () -> context.operationQueue().cancel(opId));
    }

    @NonNull
    public BleOperationHandle readRemoteRssi(@NonNull BleValueObserver<Integer> observer) {
        String opId = "ble-rssi-" + seq.incrementAndGet();
        if (!context.isConnected()) {
            BleFailure f = YoBleErrorMapper.failure(
                    BleErrorCode.CONNECTION_NOT_READY, MODULE,
                    "BLE device is not connected", context.deviceId());
            context.runtime().callback(() -> observer.onFailed(f));
            return BleOperationHandle.completed(opId);
        }

        context.operationQueue().enqueue(
                opId,
                YoBleGattOperationQueue.OpKind.READ_RSSI,
                null,
                BleOperationPriority.LOW,
                context.operationTimeoutMillis(),
                () -> {
                    YoBleGattTransport transport = context.transport();
                    if (transport == null || !transport.readRemoteRssi()) {
                        return YoBleErrorMapper.failure(
                                BleErrorCode.READ_FAILED, MODULE,
                                "readRemoteRssi returned false", context.deviceId());
                    }
                    return null;
                },
                new YoBleGattOperationQueue.CompletionHandler() {
                    @Override
                    public void onSuccess(@Nullable Object payload) {
                        int rssi = payload instanceof Integer ? (Integer) payload : 0;
                        context.runtime().callback(() -> observer.onSuccess(rssi));
                    }

                    @Override
                    public void onCancelled() {
                        context.runtime().callback(() -> observer.onFailed(YoBleErrorMapper.failure(
                                BleErrorCode.OPERATION_CANCELLED, MODULE,
                                "readRemoteRssi cancelled", context.deviceId())));
                    }

                    @Override
                    public void onFailed(@NonNull BleFailure failure) {
                        context.runtime().callback(() -> observer.onFailed(failure));
                    }
                });

        return new BleOperationHandle(opId, () -> context.operationQueue().cancel(opId));
    }

    public void onPlatformMtuChanged(int mtu, int status) {
        if (status == 0) {
            context.onMtuUpdated(mtu);
            context.notifyConnectionObservers(obs -> obs.onMtuChanged(context.deviceId(), mtu));
            context.operationQueue().succeedInFlightIfMatches(
                    YoBleGattOperationQueue.OpKind.REQUEST_MTU, mtu);
        } else {
            context.operationQueue().failInFlightIfMatches(
                    YoBleGattOperationQueue.OpKind.REQUEST_MTU,
                    YoBleErrorMapper.fromGattStatus(status, BleErrorCode.REQUEST_NOT_SUPPORTED,
                            MODULE, context.deviceId(), null, "requestMtu"));
        }
    }

    public void onPlatformConnectionUpdated(int interval, int latency, int timeout, int status) {
        cancelPriorityFallback();
        if (status == 0) {
            BleConnectionParameters params = new BleConnectionParameters(interval, latency, timeout);
            context.onConnectionParametersUpdated(params);
            context.notifyConnectionObservers(obs -> obs.onConnectionUpdated(context.deviceId(), params));
            context.operationQueue().succeedInFlightIfMatches(
                    YoBleGattOperationQueue.OpKind.CONNECTION_PRIORITY, null);
        } else {
            context.operationQueue().failInFlightIfMatches(
                    YoBleGattOperationQueue.OpKind.CONNECTION_PRIORITY,
                    YoBleErrorMapper.fromGattStatus(status, BleErrorCode.REQUEST_NOT_SUPPORTED,
                            MODULE, context.deviceId(), null, "connectionPriority"));
        }
    }

    public void onPlatformPhyUpdate(@NonNull BlePhyConfig phy, int status) {
        cancelPhyFallback();
        if (status == 0) {
            context.onPhyUpdated(phy);
            context.notifyConnectionObservers(obs -> obs.onPhyUpdated(context.deviceId(), phy));
            context.operationQueue().succeedInFlightIfMatches(
                    YoBleGattOperationQueue.OpKind.SET_PHY, phy);
        } else {
            context.operationQueue().failInFlightIfMatches(
                    YoBleGattOperationQueue.OpKind.SET_PHY,
                    YoBleErrorMapper.fromGattStatus(status, BleErrorCode.REQUEST_NOT_SUPPORTED,
                            MODULE, context.deviceId(), null, "setPreferredPhy"));
        }
    }

    public void onPlatformPhyRead(@NonNull BlePhyConfig phy, int status) {
        if (status == 0) {
            context.onPhyUpdated(phy);
            context.notifyConnectionObservers(obs -> obs.onPhyUpdated(context.deviceId(), phy));
            if (!context.operationQueue().succeedInFlightIfMatches(
                    YoBleGattOperationQueue.OpKind.READ_PHY, phy)) {
                // 若是 Android 13 兜底主动 readPhy() 响应，直接完成挂起的 SET_PHY
                context.operationQueue().succeedInFlightIfMatches(
                        YoBleGattOperationQueue.OpKind.SET_PHY, phy);
            }
        } else {
            BleFailure err = YoBleErrorMapper.fromGattStatus(status, BleErrorCode.READ_FAILED,
                    MODULE, context.deviceId(), null, "readPhy");
            if (!context.operationQueue().failInFlightIfMatches(
                    YoBleGattOperationQueue.OpKind.READ_PHY, err)) {
                context.operationQueue().failInFlightIfMatches(
                        YoBleGattOperationQueue.OpKind.SET_PHY, err);
            }
        }
    }

    public void onPlatformRssiRead(int rssi, int status) {
        if (status == 0) {
            context.onRssiUpdated(rssi);
            context.notifyConnectionObservers(obs -> obs.onRssiRead(context.deviceId(), rssi));
            context.operationQueue().succeedInFlightIfMatches(
                    YoBleGattOperationQueue.OpKind.READ_RSSI, rssi);
        } else {
            context.operationQueue().failInFlightIfMatches(
                    YoBleGattOperationQueue.OpKind.READ_RSSI,
                    YoBleErrorMapper.fromGattStatus(status, BleErrorCode.READ_FAILED,
                            MODULE, context.deviceId(), null, "readRemoteRssi"));
        }
    }

    public void cancelAllFallbacks() {
        cancelPriorityFallback();
        cancelPhyFallback();
    }

    private void armConnectionPriorityFallback() {
        cancelPriorityFallback();
        priorityFallbackHandle = context.runtime().scheduler().schedule(
                () -> context.runtime().state(() -> {
                    YoBleGattOperationQueue.QueuedOperation inFlight =
                            context.operationQueue().currentInFlight();
                    if (inFlight != null
                            && inFlight.kind() == YoBleGattOperationQueue.OpKind.CONNECTION_PRIORITY) {
                        context.timeline().record(MODULE,
                                "connectionPriority fallback completed after 200ms");
                        context.operationQueue().succeedInFlightIfMatches(
                                YoBleGattOperationQueue.OpKind.CONNECTION_PRIORITY, null);
                    }
                }),
                CONNECTION_PRIORITY_FALLBACK_MS);
    }

    private void armAndroid13PhyFallback(@NonNull String expectedSetPhyOpId) {
        cancelPhyFallback();
        phyFallbackHandle = context.runtime().scheduler().schedule(
                () -> context.runtime().state(() -> {
                    YoBleGattOperationQueue.QueuedOperation inFlight =
                            context.operationQueue().currentInFlight();
                    if (inFlight != null && expectedSetPhyOpId.equals(inFlight.operationId())) {
                        context.timeline().record(MODULE,
                                "Android 13 setPreferredPhy missing callback -> invoking readPhy() fallback");
                        YoBleGattTransport transport = context.transport();
                        if (transport == null || !transport.readPhy()) {
                            context.operationQueue().failInFlightIfMatches(
                                    YoBleGattOperationQueue.OpKind.SET_PHY,
                                    YoBleErrorMapper.failure(BleErrorCode.GATT_OPERATION_TIMEOUT,
                                            MODULE, "Android 13 PHY fallback readPhy() failed",
                                            context.deviceId()));
                        }
                    }
                }),
                ANDROID_13_PHY_FALLBACK_MS);
    }

    private void cancelPriorityFallback() {
        if (priorityFallbackHandle != null) {
            priorityFallbackHandle.cancel();
            priorityFallbackHandle = null;
        }
    }

    private void cancelPhyFallback() {
        if (phyFallbackHandle != null) {
            phyFallbackHandle.cancel();
            phyFallbackHandle = null;
        }
    }
}
