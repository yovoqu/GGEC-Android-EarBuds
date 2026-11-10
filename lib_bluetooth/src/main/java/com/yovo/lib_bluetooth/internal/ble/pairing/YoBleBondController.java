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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * 配对等待表只在 state 线程上改。取消只摘掉自己的等待者；仅当本库发起且没有其他等待者时才取消系统配对。
 */
public final class YoBleBondController implements YoReleasable {

    private static final String MODULE = "ble-bond";
    private static final long DEFAULT_BOND_TIMEOUT_MS = 30_000L;

    public static final int UNBOND_REASON_AUTH_FAILED = 1;
    public static final int UNBOND_REASON_AUTH_REJECTED = 2;
    public static final int UNBOND_REASON_AUTH_CANCELED = 3;
    public static final int UNBOND_REASON_REMOTE_DEVICE_DOWN = 4;
    public static final int UNBOND_REASON_DISCOVERY_IN_PROGRESS = 5;
    public static final int UNBOND_REASON_AUTH_TIMEOUT = 6;
    public static final int UNBOND_REASON_REPEATED_ATTEMPTS = 7;
    public static final int UNBOND_REASON_REMOTE_AUTH_CANCELED = 8;
    public static final int UNBOND_REASON_REMOVED = 9;

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
    private final Set<BleDeviceId> initiatedByLibrary = new HashSet<>();
    private final AtomicLong seq = new AtomicLong();
    private volatile boolean released;

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
        BleFailure denied = permissionPreflight(null);
        if (denied != null) {
            return Result.failure(denied);
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
        BleFailure denied = permissionPreflight(deviceId);
        if (denied != null) {
            runtime.callback(() -> observer.onFailed(denied));
            return BleOperationHandle.completed(opId);
        }
        if (!capabilityInspector.isAdapterEnabled()) {
            runtime.callback(() -> observer.onFailed(YoBleErrorMapper.failure(
                    BleErrorCode.BLUETOOTH_DISABLED, MODULE, "Bluetooth adapter disabled", deviceId)));
            return BleOperationHandle.completed(opId);
        }
        AtomicBoolean started = new AtomicBoolean(false);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        PendingBondWaiter[] slot = new PendingBondWaiter[1];
        Runnable begin = () -> beginBond(deviceId, observer, started, cancelled, slot);
        if (runtime.isStateThread()) {
            begin.run();
        } else {
            runtime.state(begin);
        }
        return new BleOperationHandle(opId, () -> runtime.state(() -> {
            cancelled.set(true);
            PendingBondWaiter waiter = slot[0];
            if (waiter == null || !removeWaiter(deviceId, waiter, true)) {
                return;
            }
            runtime.callback(() -> waiter.observer.onFailed(YoBleErrorMapper.failure(
                    BleErrorCode.OPERATION_CANCELLED, MODULE, "bond cancelled", deviceId)));
        }));
    }

    @NonNull
    public Result<Void> cancelBondProcess(@NonNull BleDeviceId deviceId) {
        BleFailure denied = permissionPreflight(deviceId);
        if (denied != null) {
            return Result.failure(denied);
        }
        YoBleBondPlatform.ActionResult res = bondPlatform.cancelBondProcess(deviceId);
        if (!res.success()) {
            return Result.failure(YoBleErrorMapper.failure(
                    res.errorCode() != null ? res.errorCode() : BleErrorCode.BOND_FAILED,
                    MODULE,
                    res.detail() != null ? res.detail() : "cancelBondProcess failed",
                    deviceId));
        }
        runtime.state(() -> failDevice(deviceId, YoBleErrorMapper.failure(
                BleErrorCode.BOND_CANCELLED, MODULE, "cancelBondProcess", deviceId)));
        timeline.record(MODULE, "cancelBondProcess: " + deviceId);
        return Result.success(null);
    }

    @NonNull
    public Result<Void> removeBond(@NonNull BleDeviceId deviceId) {
        BleFailure denied = permissionPreflight(deviceId);
        if (denied != null) {
            return Result.failure(denied);
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

    public void onPlatformBondStateChanged(@NonNull BleDeviceId deviceId,
                                           @NonNull BleBondState previous,
                                           @NonNull BleBondState current,
                                           int rawReason) {
        runtime.state(() -> applyPlatformBondState(deviceId, previous, current, rawReason));
    }

    public void onAdapterTurnedOff() {
        runtime.state(() -> failAll(YoBleErrorMapper.failure(
                BleErrorCode.BLUETOOTH_DISABLED, MODULE, "adapter turned off during bonding")));
    }

    @Override
    public void release() {
        released = true;
        observers.clear();
        runtime.state(() -> failAll(YoBleErrorMapper.failure(
                BleErrorCode.RELEASED, MODULE, "Client released")));
    }

    private void beginBond(@NonNull BleDeviceId deviceId,
                           @NonNull BleValueObserver<BleDeviceSnapshot> observer,
                           @NonNull AtomicBoolean started,
                           @NonNull AtomicBoolean cancelled,
                           @NonNull PendingBondWaiter[] slot) {
        if (cancelled.get()) {
            runtime.callback(() -> observer.onFailed(YoBleErrorMapper.failure(
                    BleErrorCode.OPERATION_CANCELLED, MODULE, "bond cancelled", deviceId)));
            return;
        }
        if (released) {
            runtime.callback(() -> observer.onFailed(YoBleErrorMapper.failure(
                    BleErrorCode.RELEASED, MODULE, "Client released", deviceId)));
            return;
        }
        BleBondState current = bondPlatform.getBondState(deviceId);
        if (current == BleBondState.BONDED) {
            snapshotStore.recordBondState(deviceId, BleBondState.BONDED, clockMillis.getAsLong());
            BleDeviceSnapshot snap = snapshotStore.get(deviceId);
            BleDeviceSnapshot resolved = snap != null ? snap : fallbackSnapshot(deviceId, BleBondState.BONDED);
            runtime.callback(() -> observer.onSuccess(resolved));
            return;
        }
        PendingBondWaiter waiter = new PendingBondWaiter(observer);
        slot[0] = waiter;
        started.set(true);
        waiters.computeIfAbsent(deviceId, key -> new ArrayList<>()).add(waiter);
        waiter.timeoutHandle = runtime.stateDelayed(
                () -> onBondTimeout(deviceId, waiter), DEFAULT_BOND_TIMEOUT_MS);
        if (current == BleBondState.BONDING) {
            timeline.record(MODULE, "already bonding, attached waiter: " + deviceId);
            return;
        }
        YoBleBondPlatform.ActionResult res = bondPlatform.createBond(deviceId);
        if (!res.success()) {
            removeWaiter(deviceId, waiter, false);
            runtime.callback(() -> observer.onFailed(YoBleErrorMapper.failure(
                    res.errorCode() != null ? res.errorCode() : BleErrorCode.BOND_FAILED,
                    MODULE,
                    res.detail() != null ? res.detail() : "createBond failed",
                    deviceId)));
            return;
        }
        initiatedByLibrary.add(deviceId);
        timeline.record(MODULE, "createBond initiated: " + deviceId);
    }

    private void applyPlatformBondState(@NonNull BleDeviceId deviceId,
                                        @NonNull BleBondState previous,
                                        @NonNull BleBondState current,
                                        int rawReason) {
        if (released) {
            return;
        }
        snapshotStore.recordBondState(deviceId, current, clockMillis.getAsLong());
        timeline.record(MODULE, "bond state " + deviceId + ": " + previous + " -> " + current
                + " (" + describeUnbondReason(rawReason) + ")");
        for (BleBondObserver obs : observers) {
            runtime.callback(() -> obs.onBondStateChanged(deviceId, previous, current, rawReason));
        }
        if (current == BleBondState.BONDED) {
            completeDevice(deviceId);
        } else if (current == BleBondState.NONE && waiters.containsKey(deviceId)) {
            failDevice(deviceId, YoBleErrorMapper.failure(
                    errorForUnbond(rawReason), MODULE,
                    "Bonding ended: " + describeUnbondReason(rawReason),
                    null, rawReason, deviceId, null, null));
        }
    }

    private void onBondTimeout(@NonNull BleDeviceId deviceId, @NonNull PendingBondWaiter waiter) {
        if (!removeWaiter(deviceId, waiter, true)) {
            return;
        }
        runtime.callback(() -> waiter.observer.onFailed(YoBleErrorMapper.failure(
                BleErrorCode.BOND_TIMEOUT, MODULE,
                "Bonding timed out after " + DEFAULT_BOND_TIMEOUT_MS + "ms", deviceId)));
    }

    private void completeDevice(@NonNull BleDeviceId deviceId) {
        List<PendingBondWaiter> list = takeWaiters(deviceId);
        BleDeviceSnapshot snap = snapshotStore.get(deviceId);
        BleDeviceSnapshot resolved = snap != null ? snap : fallbackSnapshot(deviceId, BleBondState.BONDED);
        for (PendingBondWaiter waiter : list) {
            runtime.callback(() -> waiter.observer.onSuccess(resolved));
        }
    }

    private void failDevice(@NonNull BleDeviceId deviceId, @NonNull BleFailure failure) {
        for (PendingBondWaiter waiter : takeWaiters(deviceId)) {
            runtime.callback(() -> waiter.observer.onFailed(failure));
        }
    }

    private void failAll(@NonNull BleFailure failure) {
        List<BleDeviceId> devices = new ArrayList<>(waiters.keySet());
        for (BleDeviceId deviceId : devices) {
            BleFailure scoped = YoBleErrorMapper.failure(
                    failure.errorCode(), MODULE, failure.message(), deviceId);
            failDevice(deviceId, scoped);
        }
    }

    private boolean removeWaiter(@NonNull BleDeviceId deviceId,
                                 @NonNull PendingBondWaiter waiter,
                                 boolean cancelPlatformIfLast) {
        cancelTimeout(waiter);
        List<PendingBondWaiter> list = waiters.get(deviceId);
        if (list == null || !list.remove(waiter)) {
            return false;
        }
        if (!list.isEmpty()) {
            return true;
        }
        waiters.remove(deviceId);
        if (cancelPlatformIfLast && initiatedByLibrary.remove(deviceId)) {
            bondPlatform.cancelBondProcess(deviceId);
        } else {
            initiatedByLibrary.remove(deviceId);
        }
        return true;
    }

    @NonNull
    private List<PendingBondWaiter> takeWaiters(@NonNull BleDeviceId deviceId) {
        initiatedByLibrary.remove(deviceId);
        List<PendingBondWaiter> list = waiters.remove(deviceId);
        if (list == null) {
            return Collections.emptyList();
        }
        for (PendingBondWaiter waiter : list) {
            cancelTimeout(waiter);
        }
        return list;
    }

    private static void cancelTimeout(@NonNull PendingBondWaiter waiter) {
        if (waiter.timeoutHandle != null) {
            waiter.timeoutHandle.cancel();
            waiter.timeoutHandle = null;
        }
    }

    @NonNull
    static BleErrorCode errorForUnbond(int rawReason) {
        switch (rawReason) {
            case UNBOND_REASON_AUTH_FAILED:
            case UNBOND_REASON_AUTH_REJECTED:
                return BleErrorCode.AUTHENTICATION_FAILED;
            case UNBOND_REASON_AUTH_CANCELED:
            case UNBOND_REASON_REMOTE_AUTH_CANCELED:
            case UNBOND_REASON_REMOVED:
                return BleErrorCode.BOND_CANCELLED;
            case UNBOND_REASON_AUTH_TIMEOUT:
                return BleErrorCode.BOND_TIMEOUT;
            default:
                return BleErrorCode.BOND_FAILED;
        }
    }

    @NonNull
    static String describeUnbondReason(int rawReason) {
        switch (rawReason) {
            case UNBOND_REASON_AUTH_FAILED: return "UNBOND_REASON_AUTH_FAILED (1)";
            case UNBOND_REASON_AUTH_REJECTED: return "UNBOND_REASON_AUTH_REJECTED (2)";
            case UNBOND_REASON_AUTH_CANCELED: return "UNBOND_REASON_AUTH_CANCELED (3)";
            case UNBOND_REASON_REMOTE_DEVICE_DOWN: return "UNBOND_REASON_REMOTE_DEVICE_DOWN (4)";
            case UNBOND_REASON_DISCOVERY_IN_PROGRESS: return "UNBOND_REASON_DISCOVERY_IN_PROGRESS (5)";
            case UNBOND_REASON_AUTH_TIMEOUT: return "UNBOND_REASON_AUTH_TIMEOUT (6)";
            case UNBOND_REASON_REPEATED_ATTEMPTS: return "UNBOND_REASON_REPEATED_ATTEMPTS (7)";
            case UNBOND_REASON_REMOTE_AUTH_CANCELED: return "UNBOND_REASON_REMOTE_AUTH_CANCELED (8)";
            case UNBOND_REASON_REMOVED: return "UNBOND_REASON_REMOVED (9)";
            default: return "UNBOND_REASON_" + rawReason;
        }
    }

    @Nullable
    private BleFailure permissionPreflight(@Nullable BleDeviceId deviceId) {
        if (released) {
            return YoBleErrorMapper.failure(BleErrorCode.RELEASED, MODULE, "Client released", deviceId);
        }
        if (deviceId != null && !BleDeviceId.isValid(deviceId.value())) {
            return YoBleErrorMapper.failure(
                    BleErrorCode.DEVICE_INVALID, MODULE, "Invalid MAC address: " + deviceId, deviceId);
        }
        if (!capabilityInspector.hasConnectPermission()) {
            return YoBleErrorMapper.failure(
                    BleErrorCode.PERMISSION_CONNECT_DENIED, MODULE,
                    "Missing BLUETOOTH_CONNECT permission", deviceId);
        }
        return null;
    }

    @NonNull
    private BleDeviceSnapshot fallbackSnapshot(@NonNull BleDeviceId deviceId, @NonNull BleBondState state) {
        long now = clockMillis.getAsLong();
        BleDeviceIdentity identity = new BleDeviceIdentity(
                deviceId, "", null, BleAddressType.UNKNOWN, BleDeviceType.UNKNOWN, now);
        return new BleDeviceSnapshot(identity, state, null, null, null, now);
    }
}
