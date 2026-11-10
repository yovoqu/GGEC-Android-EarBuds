package com.yovo.lib_bluetooth.internal.ble.gatt;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.ble.error.YoBleErrorMapper;
import com.yovo.lib_bluetooth.internal.ble.gatt.YoBleGattOperationQueue.CompletionHandler;
import com.yovo.lib_bluetooth.internal.ble.gatt.YoBleGattOperationQueue.OpKind;
import com.yovo.lib_bluetooth.internal.ble.gatt.YoBleGattOperationQueue.StartResult;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleErrorCode;
import com.yovo.lib_bluetooth.model.ble.BleFailure;
import com.yovo.lib_bluetooth.model.ble.BleGattCharacteristicInfo;
import com.yovo.lib_bluetooth.model.ble.BleGattDatabaseSnapshot;
import com.yovo.lib_bluetooth.model.ble.BleGattObserver;
import com.yovo.lib_bluetooth.model.ble.BleGattTarget;
import com.yovo.lib_bluetooth.model.ble.BleNotificationObserver;
import com.yovo.lib_bluetooth.model.ble.BleNotificationSetupMode;
import com.yovo.lib_bluetooth.model.ble.BleOperationHandle;
import com.yovo.lib_bluetooth.model.ble.BleOperationPriority;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * 按特征维护通知开关状态机。多个订阅者共享一次 CCCD 写入；
 * 丢链只把状态打回关闭并保留订阅者，重连或 Service Changed 之后再恢复。
 */
public final class YoBleNotificationRouter {

    private static final String MODULE = "ble-notify";
    private static final byte[] ENABLE_NOTIFICATION = new byte[]{0x01, 0x00};
    private static final byte[] ENABLE_INDICATION = new byte[]{0x02, 0x00};
    private static final byte[] DISABLE = new byte[]{0x00, 0x00};

    public interface RawPacketInterceptor {
        void onPacket(@NonNull BleGattTarget sourceTarget, @NonNull byte[] value);
    }

    private enum Phase {
        DISABLED, ENABLING, ENABLED, DISABLING
    }

    private static final class Subscriber {
        @NonNull
        final BleNotificationObserver observer;
        @NonNull
        final BleNotificationSetupMode mode;

        Subscriber(@NonNull BleNotificationObserver observer, @NonNull BleNotificationSetupMode mode) {
            this.observer = observer;
            this.mode = mode;
        }
    }

    private static final class Record {
        @NonNull
        Phase phase = Phase.DISABLED;
        int epoch;
        boolean allowBeforeReady;
        boolean restoring;
        boolean authTried;
        @NonNull
        final List<Subscriber> subscribers = new ArrayList<>();
        @NonNull
        final List<BleGattObserver> gattObservers = new ArrayList<>();
        int holds;
        boolean explicit;
        @NonNull
        final List<Runnable> readyWaiters = new ArrayList<>();
        @NonNull
        final List<Consumer<BleFailure>> errorWaiters = new ArrayList<>();
    }

    @NonNull
    private final YoBleSessionContext context;
    private final AtomicLong seq = new AtomicLong();
    @NonNull
    private final Map<BleGattTarget, Record> records = new LinkedHashMap<>();
    @NonNull
    private final Map<BleGattTarget, List<RawPacketInterceptor>> interceptors = new LinkedHashMap<>();
    @Nullable
    private Runnable restoreDone;
    private int restoreLeft;

    public YoBleNotificationRouter(@NonNull YoBleSessionContext context) {
        this.context = context;
    }

    @NonNull
    public Runnable subscribe(@NonNull BleGattTarget target,
                              @NonNull BleNotificationSetupMode mode,
                              @NonNull BleNotificationObserver observer) {
        return subscribe(target, mode, observer, false);
    }

    @NonNull
    public Runnable subscribeDuringSetup(@NonNull BleGattTarget target,
                                         @NonNull BleNotificationSetupMode mode,
                                         @NonNull BleNotificationObserver observer) {
        return subscribe(target, mode, observer, true);
    }

    @NonNull
    private Runnable subscribe(@NonNull BleGattTarget target,
                               @NonNull BleNotificationSetupMode mode,
                               @NonNull BleNotificationObserver observer,
                               boolean allowBeforeReady) {
        context.runtime().checkStateThread();
        BleGattTarget key = target.withoutDescriptor();
        Record record = record(key);
        record.allowBeforeReady = record.allowBeforeReady || allowBeforeReady;
        record.subscribers.add(new Subscriber(observer, mode));
        if (record.phase == Phase.ENABLED) {
            context.runtime().callback(() -> observer.onNotificationEnabled(context.deviceId(), key));
        }
        reconcile(key, record);
        return () -> context.runtime().state(() -> unsubscribe(key, observer));
    }

    @NonNull
    public BleOperationHandle setNotify(@NonNull BleGattTarget target,
                                        boolean enabled,
                                        @NonNull BleGattObserver observer) {
        context.runtime().checkStateThread();
        String opId = "ble-set-notify-" + seq.incrementAndGet();
        BleGattTarget key = target.withoutDescriptor();
        Record record = record(key);
        if (enabled) {
            record.explicit = true;
            record.gattObservers.add(observer);
        } else {
            record.gattObservers.remove(observer);
            record.explicit = !record.gattObservers.isEmpty();
        }
        reconcile(key, record);
        return new BleOperationHandle(opId, () -> context.runtime().state(() -> {
            Record current = records.get(key);
            if (current == null) {
                return;
            }
            current.gattObservers.remove(observer);
            current.explicit = !current.gattObservers.isEmpty();
            reconcile(key, current);
        }));
    }

    /** 请求-响应占用一次使能。返回的 Runnable 在 state 线程释放占用。 */
    public void ensureEnabled(@NonNull BleGattTarget target,
                              @NonNull BleNotificationSetupMode mode,
                              @NonNull Runnable onReady,
                              @NonNull Consumer<BleFailure> onError) {
        context.runtime().checkStateThread();
        BleGattTarget key = target.withoutDescriptor();
        Record record = record(key);
        record.holds++;
        if (record.phase == Phase.ENABLED) {
            onReady.run();
            return;
        }
        record.readyWaiters.add(onReady);
        record.errorWaiters.add(onError);
        reconcile(key, record);
    }

    public void releaseHold(@NonNull BleGattTarget target) {
        context.runtime().checkStateThread();
        BleGattTarget key = target.withoutDescriptor();
        Record record = records.get(key);
        if (record == null || record.holds <= 0) {
            return;
        }
        record.holds--;
        reconcile(key, record);
    }

    public void registerInterceptor(@NonNull BleGattTarget target, @NonNull RawPacketInterceptor interceptor) {
        context.runtime().checkStateThread();
        interceptors.computeIfAbsent(target.withoutDescriptor(), ignored -> new ArrayList<>()).add(interceptor);
    }

    public void unregisterInterceptor(@NonNull BleGattTarget target, @NonNull RawPacketInterceptor interceptor) {
        context.runtime().checkStateThread();
        List<RawPacketInterceptor> list = interceptors.get(target.withoutDescriptor());
        if (list != null) {
            list.remove(interceptor);
        }
    }

    public void dispatchCharacteristicChanged(@NonNull BleGattTarget source, @NonNull byte[] value) {
        context.runtime().checkStateThread();
        byte[] copy = value.clone();
        BleDeviceId deviceId = context.deviceId();
        for (Map.Entry<BleGattTarget, List<RawPacketInterceptor>> entry : interceptors.entrySet()) {
            if (!entry.getKey().matches(source.withoutDescriptor())) {
                continue;
            }
            for (RawPacketInterceptor interceptor : new ArrayList<>(entry.getValue())) {
                try {
                    interceptor.onPacket(source, copy);
                } catch (Throwable t) {
                    context.timeline().record(MODULE, "interceptor threw: " + t);
                }
            }
        }
        for (Map.Entry<BleGattTarget, Record> entry : records.entrySet()) {
            if (!entry.getKey().matches(source.withoutDescriptor())) {
                continue;
            }
            Record record = entry.getValue();
            BleGattTarget key = entry.getKey();
            for (Subscriber subscriber : new ArrayList<>(record.subscribers)) {
                context.runtime().callback(() -> subscriber.observer.onCharacteristicChanged(deviceId, key, copy));
            }
            for (BleGattObserver observer : new ArrayList<>(record.gattObservers)) {
                context.runtime().callback(() -> observer.onCharacteristicChanged(deviceId, key, copy));
            }
        }
    }

    /** 链路还在会话里，但 GATT 已断开：订阅者保留，进行中的 CCCD 回调作废。 */
    public void onLinkLost() {
        context.runtime().checkStateThread();
        BleFailure lost = YoBleErrorMapper.failure(
                BleErrorCode.LINK_LOSS, MODULE, "link lost before notification setup finished", context.deviceId());
        for (Record record : records.values()) {
            record.epoch++;
            record.phase = Phase.DISABLED;
            failWaiters(record, lost);
        }
    }

    public void restoreSubscriptions(@NonNull Runnable onDone) {
        context.runtime().checkStateThread();
        restoreDone = onDone;
        restoreLeft = 0;
        for (Map.Entry<BleGattTarget, Record> entry : records.entrySet()) {
            Record record = entry.getValue();
            if (record.phase == Phase.DISABLED && desired(record)) {
                record.restoring = true;
                restoreLeft++;
                reconcile(entry.getKey(), record);
            }
        }
        if (restoreLeft == 0 && restoreDone != null) {
            Runnable done = restoreDone;
            restoreDone = null;
            done.run();
        }
    }

    public void endSession(boolean userInitiated, @Nullable BleFailure failure) {
        context.runtime().checkStateThread();
        BleDeviceId deviceId = context.deviceId();
        for (Map.Entry<BleGattTarget, Record> entry : records.entrySet()) {
            BleGattTarget key = entry.getKey();
            Record record = entry.getValue();
            record.epoch++;
            if (failure != null) {
                failWaiters(record, failure);
            } else {
                record.readyWaiters.clear();
                record.errorWaiters.clear();
            }
            for (Subscriber subscriber : record.subscribers) {
                if (userInitiated) {
                    context.runtime().callback(() -> subscriber.observer.onNotificationDisabled(deviceId, key));
                } else if (failure != null) {
                    BleFailure reported = failure;
                    context.runtime().callback(() -> subscriber.observer.onNotificationFailed(reported));
                }
            }
        }
        records.clear();
        interceptors.clear();
        restoreDone = null;
        restoreLeft = 0;
    }

    private void unsubscribe(@NonNull BleGattTarget key, @NonNull BleNotificationObserver observer) {
        Record record = records.get(key);
        if (record == null) {
            return;
        }
        record.subscribers.removeIf(subscriber -> subscriber.observer == observer);
        if (record.phase == Phase.ENABLED) {
            context.runtime().callback(() -> observer.onNotificationDisabled(context.deviceId(), key));
        }
        reconcile(key, record);
    }

    private void reconcile(@NonNull BleGattTarget key, @NonNull Record record) {
        boolean want = desired(record);
        if (want && (record.phase == Phase.DISABLED || record.phase == Phase.DISABLING)) {
            beginChange(key, record, true);
        } else if (!want && (record.phase == Phase.ENABLED || record.phase == Phase.ENABLING)) {
            beginChange(key, record, false);
        } else if (!want && record.phase == Phase.DISABLED && record.subscribers.isEmpty()
                && record.holds == 0 && record.gattObservers.isEmpty()) {
            records.remove(key, record);
        }
    }

    private void beginChange(@NonNull BleGattTarget key, @NonNull Record record, boolean enable) {
        if (!context.isReady() && !(enable && record.allowBeforeReady && context.isConnected())) {
            if (!enable) {
                record.phase = Phase.DISABLED;
            }
            return;
        }
        BleGattDatabaseSnapshot database = context.currentDatabase();
        BleGattCharacteristicInfo info = database == null ? null : database.findCharacteristic(key);
        if (info == null) {
            failAll(key, record, YoBleErrorMapper.failure(
                    BleErrorCode.CHARACTERISTIC_NOT_FOUND, MODULE,
                    "characteristic missing for notification", context.deviceId()));
            return;
        }
        boolean hasCccd = info.findDescriptor(BleGattDatabaseSnapshot.CCCD_UUID) != null;
        BleNotificationSetupMode mode = record.subscribers.isEmpty()
                ? BleNotificationSetupMode.DEFAULT : record.subscribers.get(0).mode;
        int epoch = record.epoch;
        record.phase = enable ? Phase.ENABLING : Phase.DISABLING;
        if (!hasCccd && (mode == BleNotificationSetupMode.COMPAT || !enable)) {
            localSwitch(key, enable);
            finishChange(key, record, epoch, enable, null);
            return;
        }
        if (!hasCccd) {
            finishChange(key, record, epoch, enable, YoBleErrorMapper.failure(
                    BleErrorCode.DESCRIPTOR_NOT_FOUND, MODULE, "CCCD missing", context.deviceId()));
            return;
        }
        if (enable && mode == BleNotificationSetupMode.QUICK_SETUP) {
            localSwitch(key, true);
            finishChange(key, record, epoch, true, null);
        }
        byte[] cccd = enable ? cccdValue(info) : DISABLE;
        BleGattTarget cccdTarget = BleGattTarget.descriptor(
                key.serviceUuid(), key.serviceInstanceId(),
                key.characteristicUuid(), key.characteristicInstanceId(),
                BleGattDatabaseSnapshot.CCCD_UUID);
        String opId = "ble-cccd-" + seq.incrementAndGet();
        context.operationQueue().enqueue(
                opId, OpKind.WRITE_DESC, cccdTarget, BleOperationPriority.HIGH,
                context.operationTimeoutMillis(),
                () -> {
                    YoBleGattTransport transport = context.transport();
                    if (transport == null) {
                        return StartResult.failed(YoBleErrorMapper.failure(
                                BleErrorCode.CONNECTION_NOT_READY, MODULE, "transport closed", context.deviceId()));
                    }
                    transport.setCharacteristicNotification(key, enable);
                    if (!transport.writeDescriptor(cccdTarget, cccd)) {
                        return StartResult.failed(YoBleErrorMapper.failure(
                                BleErrorCode.NOTIFY_FAILED, MODULE, "CCCD write rejected", context.deviceId()));
                    }
                    return StartResult.pending();
                },
                new CompletionHandler() {
                    @Override
                    public void onSuccess(@Nullable Object payload) {
                        if (mode == BleNotificationSetupMode.QUICK_SETUP && enable) {
                            return;
                        }
                        finishChange(key, record, epoch, enable, null);
                    }

                    @Override
                    public void onCancelled() {
                        finishChange(key, record, epoch, enable, YoBleErrorMapper.failure(
                                BleErrorCode.OPERATION_CANCELLED, MODULE, "CCCD write cancelled", context.deviceId()));
                    }

                    @Override
                    public void onFailed(@NonNull BleFailure failure) {
                        if (record.authTried) {
                            finishChange(key, record, epoch, enable, failure);
                            return;
                        }
                        record.authTried = true;
                        context.recoverFromAuthError(failure,
                                () -> beginChange(key, record, enable),
                                () -> finishChange(key, record, epoch, enable, failure));
                    }
                });
    }

    private void finishChange(@NonNull BleGattTarget key,
                              @NonNull Record record,
                              int epoch,
                              boolean enable,
                              @Nullable BleFailure failure) {
        if (record.epoch != epoch || !records.containsKey(key)) {
            return;
        }
        noteRestoreFinished(record);
        if (failure != null) {
            record.phase = Phase.DISABLED;
            failAll(key, record, failure);
            return;
        }
        record.authTried = false;
        record.phase = enable ? Phase.ENABLED : Phase.DISABLED;
        BleDeviceId deviceId = context.deviceId();
        if (enable) {
            for (Subscriber subscriber : new ArrayList<>(record.subscribers)) {
                context.runtime().callback(() -> subscriber.observer.onNotificationEnabled(deviceId, key));
            }
            List<Runnable> ready = new ArrayList<>(record.readyWaiters);
            record.readyWaiters.clear();
            record.errorWaiters.clear();
            for (Runnable waiter : ready) {
                waiter.run();
            }
        } else {
            for (Subscriber subscriber : new ArrayList<>(record.subscribers)) {
                context.runtime().callback(() -> subscriber.observer.onNotificationDisabled(deviceId, key));
            }
        }
        reconcile(key, record);
    }

    private void failAll(@NonNull BleGattTarget key, @NonNull Record record, @NonNull BleFailure failure) {
        List<Subscriber> removed = new ArrayList<>(record.subscribers);
        record.subscribers.clear();
        record.explicit = false;
        record.gattObservers.clear();
        record.holds = 0;
        failWaiters(record, failure);
        for (Subscriber subscriber : removed) {
            context.runtime().callback(() -> subscriber.observer.onNotificationFailed(failure));
        }
        records.remove(key, record);
        noteRestoreFinished(record);
    }

    private void failWaiters(@NonNull Record record, @NonNull BleFailure failure) {
        List<Consumer<BleFailure>> waiters = new ArrayList<>(record.errorWaiters);
        record.readyWaiters.clear();
        record.errorWaiters.clear();
        for (Consumer<BleFailure> waiter : waiters) {
            waiter.accept(failure);
        }
    }

    private void noteRestoreFinished(@NonNull Record record) {
        if (!record.restoring || restoreDone == null || restoreLeft <= 0) {
            record.restoring = false;
            return;
        }
        record.restoring = false;
        restoreLeft--;
        if (restoreLeft == 0) {
            Runnable done = restoreDone;
            restoreDone = null;
            done.run();
        }
    }

    private void localSwitch(@NonNull BleGattTarget key, boolean enable) {
        YoBleGattTransport transport = context.transport();
        if (transport != null) {
            transport.setCharacteristicNotification(key, enable);
        }
    }

    @NonNull
    private Record record(@NonNull BleGattTarget key) {
        Record existing = find(key);
        if (existing != null) {
            return existing;
        }
        Record created = new Record();
        records.put(key, created);
        return created;
    }

    @Nullable
    private Record find(@NonNull BleGattTarget key) {
        for (Map.Entry<BleGattTarget, Record> entry : records.entrySet()) {
            if (entry.getKey().matches(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static boolean desired(@NonNull Record record) {
        return !record.subscribers.isEmpty() || record.holds > 0 || record.explicit;
    }

    @NonNull
    private static byte[] cccdValue(@NonNull BleGattCharacteristicInfo info) {
        if (info.isIndicatable() && !info.isNotifiable()) {
            return ENABLE_INDICATION;
        }
        return ENABLE_NOTIFICATION;
    }

}
