package com.yovo.lib_bluetooth.internal.ble.gatt;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.ble.diagnostics.YoBleTimelineRecorder;
import com.yovo.lib_bluetooth.internal.ble.error.YoBleErrorMapper;
import com.yovo.lib_bluetooth.internal.ble.error.YoBleGattStatus;
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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Notification / Indication 多播路由器：
 * <ul>
 *   <li>引用计数共享 CCCD：0-&gt;1 开启底层订阅与写 0x2902 CCCD，1-&gt;0 自动关闭；</li>
 *   <li>智能识别 Indicate (0x20) vs Notify (0x10) 并写入正确 CCCD 值；</li>
 *   <li>支持 DEFAULT / COMPAT / QUICK_SETUP 三种建立模式；</li>
 *   <li>支持临时请求响应拦截器挂载，杜绝收发竞态。</li>
 * </ul>
 */
public final class YoBleNotificationRouter {

    public static final byte[] ENABLE_NOTIFICATION_VALUE = new byte[]{0x01, 0x00};
    public static final byte[] ENABLE_INDICATION_VALUE = new byte[]{0x02, 0x00};
    public static final byte[] DISABLE_NOTIFICATION_VALUE = new byte[]{0x00, 0x00};

    private static final String MODULE = "ble-notify";

    public interface RawPacketInterceptor {
        /** @return true 表示消费或观测到目标数据；监听器自身决定是否自注销。 */
        void onPacket(@NonNull BleGattTarget sourceTarget, @NonNull byte[] value);
    }

    private static final class SubscriptionEntry {
        final long id;
        @NonNull
        final BleGattTarget keyTarget;
        @NonNull
        final BleNotificationObserver observer;

        SubscriptionEntry(long id,
                          @NonNull BleGattTarget keyTarget,
                          @NonNull BleNotificationObserver observer) {
            this.id = id;
            this.keyTarget = keyTarget;
            this.observer = observer;
        }
    }

    private final YoBleSessionContext context;
    private final AtomicLong subIdGen = new AtomicLong();
    private final Map<BleGattTarget, List<SubscriptionEntry>> subscriptions = new LinkedHashMap<>();
    private final Map<BleGattTarget, CopyOnWriteArrayList<RawPacketInterceptor>> interceptors =
            new LinkedHashMap<>();
    private final java.util.Set<BleGattTarget> enabledTargets = new java.util.HashSet<>();
    private final CopyOnWriteArrayList<BleGattObserver> legacyObservers = new CopyOnWriteArrayList<>();

    public YoBleNotificationRouter(@NonNull YoBleSessionContext context) {
        this.context = context;
    }

    @NonNull
    public Runnable subscribe(@NonNull BleGattTarget rawTarget,
                              @NonNull BleNotificationSetupMode setupMode,
                              @NonNull BleNotificationObserver observer) {
        return subscribeInternal(rawTarget, setupMode, false, observer);
    }

    /** 内部建链期间（DISCOVERING/INITIALIZING）启用系统特征（如 Service Changed 0x2A05）订阅。 */
    @NonNull
    public Runnable subscribeDuringSetup(@NonNull BleGattTarget rawTarget,
                                         @NonNull BleNotificationSetupMode setupMode,
                                         @NonNull BleNotificationObserver observer) {
        return subscribeInternal(rawTarget, setupMode, true, observer);
    }

    @NonNull
    private Runnable subscribeInternal(@NonNull BleGattTarget rawTarget,
                                       @NonNull BleNotificationSetupMode setupMode,
                                       boolean allowBeforeReady,
                                       @NonNull BleNotificationObserver observer) {
        BleGattTarget key = rawTarget.withoutDescriptor();
        long id = subIdGen.incrementAndGet();
        SubscriptionEntry entry = new SubscriptionEntry(id, key, observer);

        boolean needSetup;
        synchronized (this) {
            List<SubscriptionEntry> list = subscriptions.computeIfAbsent(key, k -> new ArrayList<>());
            needSetup = list.isEmpty();
            list.add(entry);
        }

        if (!needSetup) {
            context.runtime().callback(() -> observer.onNotificationEnabled(context.deviceId(), key));
        } else {
            enqueueCccdChange(key, true, setupMode, allowBeforeReady, null,
                    () -> {
                        synchronized (this) {
                            enabledTargets.add(key);
                        }
                        context.runtime().callback(() -> observer.onNotificationEnabled(context.deviceId(), key));
                    },
                    failure -> {
                        removeEntry(key, id);
                        context.runtime().callback(() -> observer.onNotificationFailed(failure));
                    });
        }

        return () -> unsubscribe(key, id, observer);
    }

    @NonNull
    public BleOperationHandle setNotifyCompat(@NonNull BleGattTarget rawTarget,
                                              boolean enabled,
                                              @NonNull BleGattObserver observer) {
        String handleId = "ble-notify-" + subIdGen.incrementAndGet();
        AtomicReference<String> queueOpIdRef = new AtomicReference<>();
        AtomicBoolean cancelled = new AtomicBoolean(false);
        BleGattTarget key = rawTarget.withoutDescriptor();
        legacyObservers.addIfAbsent(observer);

        if (enabled) {
            enqueueCccdChange(key, true, BleNotificationSetupMode.DEFAULT, false, queueOpIdRef,
                    () -> {
                        synchronized (this) {
                            enabledTargets.add(key);
                        }
                        context.runtime().callback(() ->
                                observer.onNotifyChanged(context.deviceId(), key, true));
                    },
                    failure -> context.runtime().callback(() -> observer.onFailed(failure)));
        } else {
            enqueueCccdChange(key, false, BleNotificationSetupMode.COMPAT, false, queueOpIdRef,
                    () -> {
                        synchronized (this) {
                            enabledTargets.remove(key);
                        }
                        context.runtime().callback(() ->
                                observer.onNotifyChanged(context.deviceId(), key, false));
                    },
                    failure -> context.runtime().callback(() -> observer.onFailed(failure)));
        }

        return new BleOperationHandle(handleId, () -> {
            if (cancelled.compareAndSet(false, true)) {
                context.runtime().state(() -> {
                    String qId = queueOpIdRef.get();
                    if (qId != null) {
                        context.operationQueue().cancel(qId);
                    }
                });
            }
        });
    }

    public void registerInterceptor(@NonNull BleGattTarget rawTarget,
                                    @NonNull RawPacketInterceptor interceptor) {
        BleGattTarget key = rawTarget.withoutDescriptor();
        synchronized (this) {
            interceptors.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>())
                    .addIfAbsent(interceptor);
        }
    }

    public void unregisterInterceptor(@NonNull BleGattTarget rawTarget,
                                      @NonNull RawPacketInterceptor interceptor) {
        BleGattTarget key = rawTarget.withoutDescriptor();
        synchronized (this) {
            CopyOnWriteArrayList<RawPacketInterceptor> list = interceptors.get(key);
            if (list != null) {
                list.remove(interceptor);
                if (list.isEmpty()) {
                    interceptors.remove(key);
                }
            }
        }
    }

    /** 直接保障底层 Notify/Indicate 开启（若当前会话已启用则立即放行，避免重复写 CCCD）。 */
    public void ensureEnabled(@NonNull BleGattTarget rawTarget,
                              @NonNull BleNotificationSetupMode setupMode,
                              @NonNull Runnable onReady,
                              @NonNull java.util.function.Consumer<BleFailure> onError) {
        BleGattTarget key = rawTarget.withoutDescriptor();
        synchronized (this) {
            if (enabledTargets.contains(key)) {
                onReady.run();
                return;
            }
        }
        enqueueCccdChange(key, true, setupMode, false, null, () -> {
            synchronized (this) {
                enabledTargets.add(key);
            }
            onReady.run();
        }, onError);
    }

    public void dispatchCharacteristicChanged(@NonNull BleGattTarget sourceTarget,
                                            @NonNull byte[] value) {
        BleGattTarget key = sourceTarget.withoutDescriptor();
        byte[] copy = value.clone();
        context.timeline().record(MODULE, "notify rx " + key + " "
                + YoBleTimelineRecorder.summarizeBytes(copy));

        List<BleNotificationObserver> matchedObservers = new ArrayList<>();
        List<RawPacketInterceptor> matchedInterceptors = new ArrayList<>();

        synchronized (this) {
            collectMatchedSubscriptionsLocked(key, matchedObservers);
            collectMatchedInterceptorsLocked(key, matchedInterceptors);
        }

        for (RawPacketInterceptor interceptor : matchedInterceptors) {
            try {
                interceptor.onPacket(sourceTarget, copy);
            } catch (Throwable ignored) {
            }
        }

        BleDeviceId deviceId = context.deviceId();
        for (BleNotificationObserver observer : matchedObservers) {
            context.runtime().callback(() -> observer.onCharacteristicChanged(deviceId, sourceTarget, copy));
        }
        for (BleGattObserver legacy : legacyObservers) {
            context.runtime().callback(() -> legacy.onCharacteristicChanged(deviceId, sourceTarget, copy));
        }
    }

    public synchronized void clearAll() {
        subscriptions.clear();
        interceptors.clear();
        enabledTargets.clear();
        legacyObservers.clear();
    }

    private void unsubscribe(@NonNull BleGattTarget key,
                             long entryId,
                             @NonNull BleNotificationObserver observer) {
        boolean needTeardown = removeEntry(key, entryId);
        if (needTeardown && context.isReady()) {
            enqueueCccdChange(key, false, BleNotificationSetupMode.COMPAT, false, null,
                    () -> context.runtime().callback(() ->
                            observer.onNotificationDisabled(context.deviceId(), key)),
                    ignored -> context.runtime().callback(() ->
                            observer.onNotificationDisabled(context.deviceId(), key)));
        } else {
            context.runtime().callback(() ->
                    observer.onNotificationDisabled(context.deviceId(), key));
        }
    }

    private synchronized boolean removeEntry(@NonNull BleGattTarget key, long id) {
        List<SubscriptionEntry> list = subscriptions.get(key);
        if (list == null) {
            return false;
        }
        Iterator<SubscriptionEntry> it = list.iterator();
        while (it.hasNext()) {
            if (it.next().id == id) {
                it.remove();
                break;
            }
        }
        if (list.isEmpty()) {
            subscriptions.remove(key);
            enabledTargets.remove(key);
            return true;
        }
        return false;
    }

    private void enqueueCccdChange(@NonNull BleGattTarget charTarget,
                                   boolean enable,
                                   @NonNull BleNotificationSetupMode setupMode,
                                   boolean allowBeforeReady,
                                   @Nullable AtomicReference<String> queueOpIdOut,
                                   @NonNull Runnable onSuccess,
                                   @NonNull java.util.function.Consumer<BleFailure> onFailed) {
        boolean allowedState = allowBeforeReady ? context.isConnected() : context.isReady();
        if (!allowedState) {
            onFailed.accept(YoBleErrorMapper.failure(
                    BleErrorCode.CONNECTION_NOT_READY, MODULE,
                    "BLE connection is not READY for notification setup",
                    context.deviceId()));
            return;
        }
        BleGattDatabaseSnapshot db = context.currentDatabase();
        BleGattCharacteristicInfo charInfo = db == null ? null : db.findCharacteristic(charTarget);
        if (db != null && charInfo == null) {
            onFailed.accept(YoBleErrorMapper.failure(
                    BleErrorCode.CHARACTERISTIC_NOT_FOUND, MODULE,
                    "Characteristic not found: " + charTarget,
                    null, BleFailure.NO_RAW_STATUS, context.deviceId(), charTarget, null));
            return;
        }

        boolean hasCccd = charInfo != null
                && charInfo.findDescriptor(BleGattDatabaseSnapshot.CCCD_UUID) != null;

        BleGattTarget cccdTarget = BleGattTarget.descriptor(
                charTarget.serviceUuid(),
                charTarget.serviceInstanceId(),
                charTarget.characteristicUuid(),
                charTarget.characteristicInstanceId(),
                BleGattDatabaseSnapshot.CCCD_UUID);

        byte[] cccdValue = resolveCccdValue(charInfo, enable);

        if (setupMode == BleNotificationSetupMode.QUICK_SETUP && enable) {
            YoBleGattTransport transport = context.transport();
            if (transport == null || !transport.setCharacteristicNotification(charTarget, true)) {
                onFailed.accept(YoBleErrorMapper.failure(
                        BleErrorCode.NOTIFY_FAILED, MODULE,
                        "setCharacteristicNotification(true) failed", context.deviceId()));
                return;
            }
            onSuccess.run();
            if (hasCccd) {
                context.operationQueue().enqueue(
                        "cccd-quick-" + subIdGen.incrementAndGet(),
                        YoBleGattOperationQueue.OpKind.WRITE_DESC,
                        cccdTarget,
                        BleOperationPriority.HIGH,
                        context.operationTimeoutMillis(),
                        () -> {
                            YoBleGattTransport t = context.transport();
                            if (t == null || !t.writeDescriptor(cccdTarget, cccdValue)) {
                                return YoBleErrorMapper.failure(
                                        BleErrorCode.NOTIFY_FAILED, MODULE,
                                        "Quick CCCD write failed", context.deviceId());
                            }
                            return null;
                        },
                        new YoBleGattOperationQueue.CompletionHandler() {
                            @Override
                            public void onCancelled() {
                            }

                            @Override
                            public void onFailed(@NonNull BleFailure failure) {
                                context.timeline().record(MODULE,
                                        "quick CCCD write failed: " + failure.errorCode());
                            }
                        });
            }
            return;
        }

        if (!hasCccd) {
            if (setupMode == BleNotificationSetupMode.COMPAT || !enable) {
                YoBleGattTransport transport = context.transport();
                if (transport != null && transport.setCharacteristicNotification(charTarget, enable)) {
                    onSuccess.run();
                } else {
                    onFailed.accept(YoBleErrorMapper.failure(
                            BleErrorCode.NOTIFY_FAILED, MODULE,
                            "setCharacteristicNotification failed without CCCD",
                            context.deviceId()));
                }
                return;
            }
            onFailed.accept(YoBleErrorMapper.failure(
                    BleErrorCode.DESCRIPTOR_NOT_FOUND, MODULE,
                    "CCCD (0x2902) descriptor not found on " + charTarget
                            + " (consider BleNotificationSetupMode.COMPAT)",
                    null, BleFailure.NO_RAW_STATUS, context.deviceId(), cccdTarget, null));
            return;
        }

        String qOpId = "cccd-" + (enable ? "on-" : "off-") + subIdGen.incrementAndGet();
        if (queueOpIdOut != null) {
            queueOpIdOut.set(qOpId);
        }
        context.operationQueue().enqueue(
                qOpId,
                YoBleGattOperationQueue.OpKind.WRITE_DESC,
                cccdTarget,
                BleOperationPriority.HIGH,
                context.operationTimeoutMillis(),
                () -> {
                    YoBleGattTransport transport = context.transport();
                    if (transport == null) {
                        return YoBleErrorMapper.failure(
                                BleErrorCode.CONNECTION_NOT_READY, MODULE,
                                "GATT transport closed", context.deviceId());
                    }
                    if (!transport.setCharacteristicNotification(charTarget, enable)) {
                        return YoBleErrorMapper.failure(
                                BleErrorCode.NOTIFY_FAILED, MODULE,
                                "setCharacteristicNotification(" + enable + ") returned false",
                                context.deviceId());
                    }
                    if (!transport.writeDescriptor(cccdTarget, cccdValue)) {
                        return YoBleErrorMapper.failure(
                                BleErrorCode.NOTIFY_FAILED, MODULE,
                                "writeDescriptor(CCCD) returned false",
                                null, BleFailure.NO_RAW_STATUS, context.deviceId(), cccdTarget, null);
                    }
                    return null;
                },
                new YoBleGattOperationQueue.CompletionHandler() {
                    @Override
                    public void onSuccess(@Nullable Object payload) {
                        onSuccess.run();
                    }

                    @Override
                    public void onCancelled() {
                        onFailed.accept(YoBleErrorMapper.failure(
                                BleErrorCode.OPERATION_CANCELLED, MODULE,
                                "Notification setup cancelled", context.deviceId()));
                    }

                    @Override
                    public void onFailed(@NonNull BleFailure failure) {
                        onFailedOrAuthRetry(failure, onFailed, () ->
                                enqueueCccdChange(charTarget, enable, setupMode,
                                        allowBeforeReady, queueOpIdOut, onSuccess, onFailed));
                    }
                });
    }

    /** 通知路由器在收到 onDescriptorWrite 回调后完成成功转正（若未走失败分支）。 */
    @Nullable
    public static byte[] resolveCccdValue(@Nullable BleGattCharacteristicInfo info, boolean enable) {
        if (!enable) {
            return DISABLE_NOTIFICATION_VALUE.clone();
        }
        if (info != null && info.isIndicatable() && !info.isNotifiable()) {
            return ENABLE_INDICATION_VALUE.clone();
        }
        return ENABLE_NOTIFICATION_VALUE.clone();
    }

    private void onFailedOrAuthRetry(@NonNull BleFailure failure,
                                     @NonNull java.util.function.Consumer<BleFailure> onFailed,
                                     @NonNull Runnable retryAction) {
        if (failure.rawStatusCode() != BleFailure.NO_RAW_STATUS
                && YoBleGattStatus.isAuthOrEncryptionError(failure.rawStatusCode())
                && context.handleAuthErrorIfConfigured(failure, retryAction)) {
            return;
        }
        onFailed.accept(failure);
    }

    private void collectMatchedSubscriptionsLocked(@NonNull BleGattTarget source,
                                                   @NonNull List<BleNotificationObserver> out) {
        for (Map.Entry<BleGattTarget, List<SubscriptionEntry>> entry : subscriptions.entrySet()) {
            if (matchesTargetKey(entry.getKey(), source)) {
                for (SubscriptionEntry sub : entry.getValue()) {
                    out.add(sub.observer);
                }
            }
        }
    }

    private void collectMatchedInterceptorsLocked(@NonNull BleGattTarget source,
                                                  @NonNull List<RawPacketInterceptor> out) {
        for (Map.Entry<BleGattTarget, CopyOnWriteArrayList<RawPacketInterceptor>> entry
                : interceptors.entrySet()) {
            if (matchesTargetKey(entry.getKey(), source)) {
                out.addAll(entry.getValue());
            }
        }
    }

    public static boolean matchesTargetKey(@NonNull BleGattTarget registeredKey,
                                           @NonNull BleGattTarget incoming) {
        if (!registeredKey.serviceUuid().equals(incoming.serviceUuid())
                || !registeredKey.characteristicUuid().equals(incoming.characteristicUuid())) {
            return false;
        }
        if (registeredKey.serviceInstanceId() >= 0 && incoming.serviceInstanceId() >= 0
                && registeredKey.serviceInstanceId() != incoming.serviceInstanceId()) {
            return false;
        }
        if (registeredKey.characteristicInstanceId() >= 0
                && incoming.characteristicInstanceId() >= 0
                && registeredKey.characteristicInstanceId() != incoming.characteristicInstanceId()) {
            return false;
        }
        return true;
    }
}
