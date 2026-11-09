package com.yovo.lib_bluetooth.internal.ble.pairing;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.ble.device.YoBleCapabilityInspector;
import com.yovo.lib_bluetooth.internal.ble.device.YoBleDeviceSnapshotStore;
import com.yovo.lib_bluetooth.internal.ble.diagnostics.YoBleTimelineRecorder;
import com.yovo.lib_bluetooth.internal.ble.error.YoBleErrorMapper;
import com.yovo.lib_bluetooth.internal.ble.runtime.YoBleRuntime;
import com.yovo.lib_bluetooth.internal.common.concurrency.YoTimeoutHandle;
import com.yovo.lib_bluetooth.internal.common.lifecycle.YoReleasable;
import com.yovo.lib_bluetooth.model.ble.BleAddressType;
import com.yovo.lib_bluetooth.model.ble.BleBondObserver;
import com.yovo.lib_bluetooth.model.ble.BleBondState;
import com.yovo.lib_bluetooth.model.ble.BleConfig;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleDeviceIdentity;
import com.yovo.lib_bluetooth.model.ble.BleDeviceSnapshot;
import com.yovo.lib_bluetooth.model.ble.BleDeviceType;
import com.yovo.lib_bluetooth.model.ble.BleErrorCode;
import com.yovo.lib_bluetooth.model.ble.BleFailure;
import com.yovo.lib_bluetooth.model.ble.BleOperationHandle;
import com.yovo.lib_bluetooth.model.ble.BleValueObserver;
import com.yovo.lib_bluetooth.model.common.Result;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * BLE 配对（Bond）域控制器：统一管理配对发起、取消、解绑（隐藏 API 受控）、系统状态变化追踪及会话等待协同。
 */
public final class YoBleBondController implements YoReleasable {

    private static final String MODULE = "ble-bond";
    private static final long DEFAULT_BOND_TIMEOUT_MS = 30_000L;

    private static final class PendingBondWaiter {
        @NonNull
        final BleValueObserver<BleDeviceSnapshot> observer;
        @Nullable
        YoTimeoutHandle timeoutHandle;

        PendingBondWaiter(@NonNull BleValueObserver<BleDeviceSnapshot> observer) {
            this.observer = observer;
        }
    }

    private final YoBleRuntime runtime;
    private final BleConfig config;
    private final YoBleCapabilityInspector capabilityInspector;
    private final YoBleBondPlatform bondPlatform;
    private final YoBleDeviceSnapshotStore snapshotStore;
    private final YoBleTimelineRecorder timeline;
    private final LongSupplier clockMillis;
    private final CopyOnWriteArrayList<BleBondObserver> observers = new CopyOnWriteArrayList<>();
    private final Map<BleDeviceId, List<PendingBondWaiter>> waiters = new LinkedHashMap<>();
    private final AtomicLong seq = new AtomicLong();
    private volatile boolean released = false;

    public YoBleBondController(@NonNull YoBleRuntime runtime,
                                 @NonNull BleConfig config,
                                 @NonNull YoBleCapabilityInspector capabilityInspector,
                                 @NonNull YoBleBondPlatform bondPlatform,
                                 @NonNull YoBleDeviceSnapshotStore snapshotStore,
                                 @NonNull YoBleTimelineRecorder timeline,
                                 @NonNull LongSupplier clockMillis) {
        this.runtime = runtime;
        this.config = config;
        this.capabilityInspector = capabilityInspector;
        this.bondPlatform = bondPlatform;
        this.snapshotStore = snapshotStore;
        this.timeline = timeline;
        this.clockMillis = clockMillis;
    }

    @NonNull
    public Result<List<BleDeviceSnapshot>> getBondedDevices() {
        if (released) {
            return Result.failure(YoBleErrorMapper.failure(
                    BleErrorCode.RELEASED, MODULE, "Client released"));
        }
        if (!capabilityInspector.hasConnectPermission()) {
            return Result.failure(YoBleErrorMapper.failure(
                    BleErrorCode.PERMISSION_CONNECT_DENIED, MODULE,
                    "Missing BLUETOOTH_CONNECT permission"));
        }
        if (!capabilityInspector.isBleSupported()) {
            return Result.failure(YoBleErrorMapper.failure(
                    BleErrorCode.BLE_NOT_SUPPORTED, MODULE, "BLE not supported"));
        }
        List<BleDeviceIdentity> list = bondPlatform.getBondedDevices();
        List<BleDeviceSnapshot> result = new ArrayList<>(list.size());
        long now = clockMillis.getAsLong();
        for (BleDeviceIdentity id : list) {
            snapshotStore.recordIdentity(id, BleBondState.BONDED, now);
            BleDeviceSnapshot snap = snapshotStore.get(id.deviceId());
            if (snap != null) {
                result.add(snap);
            }
        }
        return Result.success(Collections.unmodifiableList(result));
    }

    @NonNull
    public BleBondState getBondState(@NonNull BleDeviceId deviceId) {
        if (!BleDeviceId.isValid(deviceId.value())) {
            return BleBondState.UNKNOWN;
        }
        BleBondState state = bondPlatform.getBondState(deviceId);
        if (state != BleBondState.UNKNOWN) {
            snapshotStore.recordBondState(deviceId, state, clockMillis.getAsLong());
        }
        return state;
    }

    @NonNull
    public BleOperationHandle createBond(@NonNull BleDeviceId deviceId,
                                         @NonNull BleValueObserver<BleDeviceSnapshot> observer) {
        String opId = "ble-bond-" + seq.incrementAndGet();
        if (released) {
            BleFailure f = YoBleErrorMapper.failure(
                    BleErrorCode.RELEASED, MODULE, "Client released", deviceId);
            runtime.callback(() -> observer.onFailed(f));
            return BleOperationHandle.completed(opId);
        }
        if (!BleDeviceId.isValid(deviceId.value())) {
            BleFailure f = YoBleErrorMapper.failure(
                    BleErrorCode.DEVICE_INVALID, MODULE, "Invalid MAC address: " + deviceId, deviceId);
            runtime.callback(() -> observer.onFailed(f));
            return BleOperationHandle.completed(opId);
        }
        if (!capabilityInspector.hasConnectPermission()) {
            BleFailure f = YoBleErrorMapper.failure(
                    BleErrorCode.PERMISSION_CONNECT_DENIED, MODULE,
                    "Missing BLUETOOTH_CONNECT permission for createBond", deviceId);
            runtime.callback(() -> observer.onFailed(f));
            return BleOperationHandle.completed(opId);
        }
        if (!capabilityInspector.isAdapterEnabled()) {
            BleFailure f = YoBleErrorMapper.failure(
                    BleErrorCode.BLUETOOTH_DISABLED, MODULE, "Bluetooth adapter disabled", deviceId);
            runtime.callback(() -> observer.onFailed(f));
            return BleOperationHandle.completed(opId);
        }

        BleBondState current = bondPlatform.getBondState(deviceId);
        if (current == BleBondState.BONDED) {
            snapshotStore.recordBondState(deviceId, BleBondState.BONDED, clockMillis.getAsLong());
            BleDeviceSnapshot snap = snapshotStore.get(deviceId);
            BleDeviceSnapshot resolved = snap != null ? snap : fallbackSnapshot(deviceId, BleBondState.BONDED);
            runtime.callback(() -> observer.onSuccess(resolved));
            return BleOperationHandle.completed(opId);
        }

        PendingBondWaiter waiter = new PendingBondWaiter(observer);
        waiter.timeoutHandle = runtime.scheduler().schedule(
                () -> runtime.state(() -> onBondTimeout(deviceId, waiter)),
                DEFAULT_BOND_TIMEOUT_MS);

        synchronized (this) {
            waiters.computeIfAbsent(deviceId, k -> new ArrayList<>()).add(waiter);
        }

        if (current != BleBondState.BONDING) {
            YoBleBondPlatform.ActionResult res = bondPlatform.createBond(deviceId);
            if (!res.success()) {
                removeWaiter(deviceId, waiter);
                BleFailure f = YoBleErrorMapper.failure(
                        res.errorCode() != null ? res.errorCode() : BleErrorCode.BOND_FAILED,
                        MODULE,
                        res.detail() != null ? res.detail() : "createBond failed",
                        deviceId);
                runtime.callback(() -> observer.onFailed(f));
                return BleOperationHandle.completed(opId);
            }
            timeline.record(MODULE, "createBond initiated: " + deviceId);
        } else {
            timeline.record(MODULE, "already bonding, attached waiter: " + deviceId);
        }

        return new BleOperationHandle(opId, () -> {
            removeWaiter(deviceId, waiter);
            cancelBondProcess(deviceId);
        });
    }

    @NonNull
    public Result<Void> cancelBondProcess(@NonNull BleDeviceId deviceId) {
        if (released) {
            return Result.failure(YoBleErrorMapper.failure(
                    BleErrorCode.RELEASED, MODULE, "Client released", deviceId));
        }
        if (!capabilityInspector.hasConnectPermission()) {
            return Result.failure(YoBleErrorMapper.failure(
                    BleErrorCode.PERMISSION_CONNECT_DENIED, MODULE,
                    "Missing BLUETOOTH_CONNECT permission", deviceId));
        }
        YoBleBondPlatform.ActionResult res = bondPlatform.cancelBondProcess(deviceId);
        if (!res.success()) {
            return Result.failure(YoBleErrorMapper.failure(
                    res.errorCode() != null ? res.errorCode() : BleErrorCode.BOND_FAILED,
                    MODULE,
                    res.detail() != null ? res.detail() : "cancelBondProcess failed",
                    deviceId));
        }
        timeline.record(MODULE, "cancelBondProcess: " + deviceId);
        return Result.success(null);
    }

    @NonNull
    public Result<Void> removeBond(@NonNull BleDeviceId deviceId) {
        if (released) {
            return Result.failure(YoBleErrorMapper.failure(
                    BleErrorCode.RELEASED, MODULE, "Client released", deviceId));
        }
        if (!capabilityInspector.hasConnectPermission()) {
            return Result.failure(YoBleErrorMapper.failure(
                    BleErrorCode.PERMISSION_CONNECT_DENIED, MODULE,
                    "Missing BLUETOOTH_CONNECT permission", deviceId));
        }
        if (!config.allowHiddenApiBondRemoval()) {
            return Result.failure(YoBleErrorMapper.failure(
                    BleErrorCode.HIDDEN_API_BLOCKED, MODULE,
                    "removeBond requires BleConfig.allowHiddenApiBondRemoval(true)",
                    deviceId));
        }
        YoBleBondPlatform.ActionResult res = bondPlatform.removeBond(deviceId, true);
        if (!res.success()) {
            return Result.failure(YoBleErrorMapper.failure(
                    res.errorCode() != null ? res.errorCode() : BleErrorCode.BOND_FAILED,
                    MODULE,
                    res.detail() != null ? res.detail() : "removeBond failed",
                    deviceId));
        }
        snapshotStore.recordBondState(deviceId, BleBondState.NONE, clockMillis.getAsLong());
        timeline.record(MODULE, "removeBond succeeded: " + deviceId);
        return Result.success(null);
    }

    @NonNull
    public BleOperationHandle observeBondState(@NonNull BleBondObserver observer) {
        String opId = "ble-bond-obs-" + seq.incrementAndGet();
        observers.addIfAbsent(observer);
        return new BleOperationHandle(opId, () -> observers.remove(observer));
    }

    /** 由系统 BroadcastReceiver 推送配对状态迁移事件。 */
    public void onPlatformBondStateChanged(@NonNull BleDeviceId deviceId,
                                           @NonNull BleBondState previous,
                                           @NonNull BleBondState current,
                                           int rawReason) {
        if (released) {
            return;
        }
        snapshotStore.recordBondState(deviceId, current, clockMillis.getAsLong());
        timeline.record(MODULE, "bond state " + deviceId + ": " + previous + " -> " + current
                + " (reason=" + rawReason + ")");

        for (BleBondObserver obs : observers) {
            runtime.callback(() -> obs.onBondStateChanged(deviceId, previous, current, rawReason));
        }

        if (current == BleBondState.BONDED) {
            List<PendingBondWaiter> list = drainWaiters(deviceId);
            BleDeviceSnapshot snap = snapshotStore.get(deviceId);
            BleDeviceSnapshot resolved = snap != null ? snap : fallbackSnapshot(deviceId, BleBondState.BONDED);
            for (PendingBondWaiter w : list) {
                if (w.timeoutHandle != null) {
                    w.timeoutHandle.cancel();
                }
                runtime.callback(() -> w.observer.onSuccess(resolved));
            }
        } else if (current == BleBondState.NONE) {
            List<PendingBondWaiter> list = drainWaiters(deviceId);
            if (!list.isEmpty()) {
                BleErrorCode code = rawReason == 9 /* CANCEL / REMOVED */ || rawReason == 0
                        ? BleErrorCode.BOND_FAILED : BleErrorCode.AUTHENTICATION_FAILED;
                BleFailure f = YoBleErrorMapper.failure(
                        code, MODULE,
                        "Bonding aborted or rejected (reason=" + rawReason + ")",
                        null, rawReason, deviceId, null, null);
                for (PendingBondWaiter w : list) {
                    if (w.timeoutHandle != null) {
                        w.timeoutHandle.cancel();
                    }
                    runtime.callback(() -> w.observer.onFailed(f));
                }
            }
        }
    }

    @NonNull
    private BleDeviceSnapshot fallbackSnapshot(@NonNull BleDeviceId deviceId,
                                               @NonNull BleBondState state) {
        long now = clockMillis.getAsLong();
        BleDeviceIdentity identity = new BleDeviceIdentity(
                deviceId, "", null, BleAddressType.UNKNOWN, BleDeviceType.UNKNOWN, now);
        return new BleDeviceSnapshot(identity, state, null, null, null, now);
    }

    @Override
    public void release() {
        released = true;
        observers.clear();
        synchronized (this) {
            for (List<PendingBondWaiter> list : waiters.values()) {
                for (PendingBondWaiter w : list) {
                    if (w.timeoutHandle != null) {
                        w.timeoutHandle.cancel();
                    }
                }
            }
            waiters.clear();
        }
    }

    private void onBondTimeout(@NonNull BleDeviceId deviceId, @NonNull PendingBondWaiter waiter) {
        if (!removeWaiter(deviceId, waiter)) {
            return;
        }
        BleFailure f = YoBleErrorMapper.failure(
                BleErrorCode.GATT_OPERATION_TIMEOUT, MODULE,
                "Bonding timed out after " + DEFAULT_BOND_TIMEOUT_MS + "ms", deviceId);
        runtime.callback(() -> waiter.observer.onFailed(f));
    }

    private synchronized boolean removeWaiter(@NonNull BleDeviceId deviceId,
                                              @NonNull PendingBondWaiter waiter) {
        if (waiter.timeoutHandle != null) {
            waiter.timeoutHandle.cancel();
            waiter.timeoutHandle = null;
        }
        List<PendingBondWaiter> list = waiters.get(deviceId);
        if (list == null) {
            return false;
        }
        boolean removed = list.remove(waiter);
        if (list.isEmpty()) {
            waiters.remove(deviceId);
        }
        return removed;
    }

    @NonNull
    private synchronized List<PendingBondWaiter> drainWaiters(@NonNull BleDeviceId deviceId) {
        List<PendingBondWaiter> list = waiters.remove(deviceId);
        return list == null ? Collections.emptyList() : list;
    }
}
