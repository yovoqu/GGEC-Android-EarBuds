package com.yovo.lib_bluetooth.internal.classic.discovery;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.common.bluetooth.radio.YoBtRadioCoordinator;
import com.yovo.lib_bluetooth.internal.common.bluetooth.radio.YoBtRadioLease;
import com.yovo.lib_bluetooth.spi.permission.YoBluetoothPermission;
import com.yovo.lib_bluetooth.internal.common.permission.YoBluetoothPermissionChecker;
import com.yovo.lib_bluetooth.internal.common.result.YoResult;
import com.yovo.lib_bluetooth.internal.classic.device.capability.YoCbtDeviceCapability;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceClass;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceIdentity;
import com.yovo.lib_bluetooth.internal.classic.device.snapshot.YoCbtDeviceSnapshot;
import com.yovo.lib_bluetooth.internal.classic.device.snapshot.YoCbtObservedDevice;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtErrorCode;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtFailure;
import com.yovo.lib_bluetooth.internal.classic.link.YoCbtAclLinkState;
import com.yovo.lib_bluetooth.internal.classic.pairing.YoCbtBondState;
import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtCapabilitySource;
import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtProfileType;
import com.yovo.lib_bluetooth.internal.classic.runtime.YoCbtEvents;
import com.yovo.lib_bluetooth.internal.classic.runtime.YoCbtRuntime;
import com.yovo.lib_bluetooth.provider.lib_common.log.YoBtLogger;

import java.util.Collections;
import java.util.EnumMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 经典蓝牙设备发现控制器，编排扫描会话、权限校验与结果合并。
 */
public final class YoCbtDiscoveryController {

    private static final String TAG = "YoCbtDiscoveryController";
    private static final String SOURCE_MODULE = "yocbt:discovery:controller";

    /** 一次条件重试的调度延迟：只用于吸收前一次 cancelDiscovery 尚未落定的窗口。 */
    private static final long DISCOVERY_START_RETRY_DELAY_MILLIS = 80L;

    private final Context appContext;
    private final YoCbtRuntime runtime;
    private final YoBluetoothPermissionChecker permissionChecker;
    private final YoCbtDiscoveryPlatform discoveryPlatform;
    private final YoCbtDiscoveryBroadcastPort discoveryBroadcastPort;
    @Nullable
    private final YoCbtDiscoveryResultMerger resultMerger;
    private final YoBtRadioCoordinator radioCoordinator;
    private volatile YoBtRadioLease activeRadioLease;

    private final AtomicReference<YoCbtDiscoveryState> state =
            new AtomicReference<>(YoCbtDiscoveryState.IDLE);
    private final AtomicReference<YoCbtDiscoveryOperationContext> activeSession = new AtomicReference<>(null);

    public YoCbtDiscoveryController(@NonNull Context appContext,
                                    @NonNull YoCbtRuntime runtime,
                                    @NonNull YoBluetoothPermissionChecker permissionChecker,
                                    @NonNull YoCbtDiscoveryPlatform discoveryPlatform,
                                    @NonNull YoCbtDiscoveryBroadcastPort discoveryBroadcastPort,
                                    @Nullable YoCbtDiscoveryResultMerger resultMerger,
                                    @NonNull YoBtRadioCoordinator radioCoordinator) {
        this.appContext = appContext.getApplicationContext();
        this.runtime = runtime;
        this.permissionChecker = permissionChecker;
        this.discoveryPlatform = discoveryPlatform;
        this.discoveryBroadcastPort = discoveryBroadcastPort;
        this.resultMerger = resultMerger;
        this.radioCoordinator = java.util.Objects.requireNonNull(radioCoordinator, "radioCoordinator cannot be null");
    }

    @NonNull
    public YoResult<Void> start(@NonNull YoCbtDiscoveryConfig config,
                                @NonNull YoCbtDiscoveryObserver observer) {
        if (state.get() == YoCbtDiscoveryState.RELEASED) {
            return YoResult.failure(YoCbtFailure.of(
                    YoCbtErrorCode.YO_CBT_RELEASED,
                    "Discovery controller has been released",
                    SOURCE_MODULE));
        }

        if (!permissionChecker.has(appContext, YoBluetoothPermission.SCAN)) {
            return YoResult.failure(YoCbtFailure.of(
                    YoCbtErrorCode.YO_CBT_PERMISSION_SCAN_DENIED,
                    "BLUETOOTH_SCAN permission is not granted",
                    SOURCE_MODULE));
        }
        if (discoveryPlatform.requiresLocationPermission()
                && !discoveryPlatform.hasLocationPermission(appContext)) {
            return YoResult.failure(YoCbtFailure.of(
                    YoCbtErrorCode.YO_CBT_PERMISSION_LOCATION_DENIED,
                    "Location permission is required for classic Bluetooth discovery on this Android version",
                    SOURCE_MODULE));
        }

        if (!discoveryPlatform.isSupported()) {
            return YoResult.failure(YoCbtFailure.of(
                    YoCbtErrorCode.YO_CBT_BLUETOOTH_NOT_SUPPORTED,
                    "Bluetooth is not supported on this device",
                    SOURCE_MODULE));
        }
        if (!discoveryPlatform.isEnabled()) {
            return YoResult.failure(YoCbtFailure.of(
                    YoCbtErrorCode.YO_CBT_BLUETOOTH_DISABLED,
                    "Bluetooth is disabled on this device",
                    SOURCE_MODULE));
        }

        YoCbtDiscoveryOperationContext oldSession = activeSession.get();
        if (oldSession != null && oldSession.completed.get()) {
            state.set(YoCbtDiscoveryState.IDLE);
        }

        if (!state.compareAndSet(YoCbtDiscoveryState.IDLE, YoCbtDiscoveryState.PREPARING)) {
            return YoResult.failure(YoCbtFailure.of(
                    YoCbtErrorCode.YO_CBT_DISCOVERY_ALREADY_RUNNING,
                    "Discovery is already in progress: " + state.get(),
                    SOURCE_MODULE));
        }

        if (runtime.isReleased()) {
            state.set(YoCbtDiscoveryState.IDLE);
            return YoResult.failure(YoCbtFailure.of(
                    YoCbtErrorCode.YO_CBT_RELEASED,
                    "Runtime has been released",
                    SOURCE_MODULE));
        }

        YoCbtDiscoveryOperationContext session = new YoCbtDiscoveryOperationContext(config, observer);
        activeSession.set(session);
        runtime.state(() -> initiateDiscovery(session));
        return YoResult.success(null);
    }

    @NonNull
    public YoResult<Void> stop() {
        YoCbtDiscoveryOperationContext session = activeSession.get();
        if (session == null || session.completed.get()) {
            return YoResult.failure(YoCbtFailure.of(
                    YoCbtErrorCode.YO_CBT_DISCOVERY_ALREADY_RUNNING,
                    "No active discovery session to stop",
                    SOURCE_MODULE));
        }

        YoCbtDiscoveryState current = state.get();
        if (current != YoCbtDiscoveryState.DISCOVERING
                && current != YoCbtDiscoveryState.PREPARING) {
            return YoResult.failure(YoCbtFailure.of(
                    YoCbtErrorCode.YO_CBT_DISCOVERY_ALREADY_RUNNING,
                    "Cannot stop discovery in state: " + current,
                    SOURCE_MODULE));
        }

        // 同步标记 session 完成并请求底层取消发现，防止异步排队延迟导致后续立即 start 触发冲突
        session.completed.set(true);
        cancelRetry(session);
        if (session.timeoutHandle != null) {
            session.timeoutHandle.cancel();
            session.timeoutHandle = null;
        }
        discoveryPlatform.cancelDiscovery();

        runtime.state(() -> {
            YoCbtDiscoveryOperationContext s = activeSession.get();
            if (s == null) return;
            state.set(YoCbtDiscoveryState.STOPPING);
            notifyStateChanged(s, YoCbtDiscoveryState.STOPPING);
            stopDiscoveryInternal(s);
        });
        return YoResult.success(null);
    }

    public void cancelForConnect() {
        YoCbtDiscoveryOperationContext session = activeSession.getAndSet(null);
        if (session == null) return;

        session.completed.set(true);
        cancelRetry(session);
        releaseSessionScope(session);
        if (session.timeoutHandle != null) {
            session.timeoutHandle.cancel();
            session.timeoutHandle = null;
        }

        releaseRadioLease();
        discoveryPlatform.cancelDiscovery();
        clearReceiverListener();
        state.set(YoCbtDiscoveryState.IDLE);
        YoBtLogger.d(TAG, "cancelForConnect：发现流程已取消");
    }

    public boolean isDiscovering() {
        if (!discoveryPlatform.isDiscovering()) return false;
        YoCbtDiscoveryOperationContext session = activeSession.get();
        return session != null
                && !session.completed.get()
                && state.get() == YoCbtDiscoveryState.DISCOVERING;
    }

    public void release() {
        YoCbtDiscoveryState previous = state.getAndSet(YoCbtDiscoveryState.RELEASED);
        if (previous == YoCbtDiscoveryState.RELEASED) return;

        YoCbtDiscoveryOperationContext session = activeSession.getAndSet(null);
        if (session != null) {
            session.completed.set(true);
            cancelRetry(session);
            if (session.timeoutHandle != null) {
                session.timeoutHandle.cancel();
                session.timeoutHandle = null;
            }
        }

        releaseRadioLease();
        discoveryPlatform.cancelDiscovery();
        clearReceiverListener();
        YoBtLogger.d(TAG, "发现控制器已释放");
    }

    private void onDiscoveryStarted() {
        YoCbtDiscoveryOperationContext session = activeSession.get();
        if (session == null || session.completed.get()) return;
        session.hardwareStarted.set(true);
        YoBtLogger.d(TAG, "收到 ACTION_DISCOVERY_STARTED：操作=" + session.operationId);
    }

    private void onDiscoveryFinished() {
        YoCbtDiscoveryOperationContext session = activeSession.get();
        if (session == null || session.completed.get()) return;

        // 若当前 session 刚发起，系统尚未派发当前 session 的 ACTION_DISCOVERY_STARTED，
        // 则该 finish 广播是前序已取消 session 的残留广播，予以吸收丢弃，避免误伤当前新发现会话
        if (!session.hardwareStarted.get()) {
            YoBtLogger.d(TAG, "忽略残留的 ACTION_DISCOVERY_FINISHED 广播：操作=" + session.operationId);
            return;
        }

        completeDiscoveryNormally(session);
    }

    private void completeDiscoveryNormally(@NonNull YoCbtDiscoveryOperationContext session) {
        if (!session.completed.compareAndSet(false, true)) return;

        cancelRetry(session);

        if (session.timeoutHandle != null) {
            session.timeoutHandle.cancel();
            session.timeoutHandle = null;
        }

        releaseRadioLease();
        discoveryPlatform.cancelDiscovery();
        clearReceiverListener();
        activeSession.compareAndSet(session, null);

        state.set(YoCbtDiscoveryState.FINISHED);
        notifyStateChanged(session, YoCbtDiscoveryState.FINISHED);
        state.set(YoCbtDiscoveryState.IDLE);

        YoBtLogger.d(TAG, "发现完成：操作=" + session.operationId
                + "，设备数=" + session.results.size());
        publishDiscoveryEvent(new YoCbtDiscoveryEvent.DiscoveryFinishedEvent(
                session.eventSession, session.results.size(), System.currentTimeMillis()));
    }

    private void onDeviceFound(@NonNull YoCbtDiscoveryDeviceFound found) {
        YoCbtDiscoveryOperationContext session = activeSession.get();
        if (session == null || session.completed.get()) return;

        YoCbtDeviceIdentity identity = found.getIdentity();
        YoCbtDeviceClass deviceClass = found.getDeviceClass();
        int rssi = found.getRssi();
        long now = System.currentTimeMillis();

        YoCbtDeviceId deviceId = identity.getDeviceId();
        YoCbtDiscoveryResultEntry entry = session.results.get(deviceId);
        boolean isNew = false;
        if (entry != null) {
            entry.rssi = rssi;
            entry.lastSeenMillis = now;
            if (!identity.getDisplayName().isEmpty()
                    && entry.identity.getDisplayName().isEmpty()) {
                entry.identity = identity;
            }
        } else {
            entry = new YoCbtDiscoveryResultEntry(identity, deviceClass, rssi, now);
            session.results.put(deviceId, entry);
            isNew = true;
        }

        YoCbtObservedDevice observed = new YoCbtObservedDevice(
                entry.identity, entry.rssi, entry.deviceClass, now);
        if (!session.filter.accept(observed)) return;

        YoCbtDeviceSnapshot snapshot = resultMerger != null
                ? resultMerger.merge(observed)
                : buildSnapshot(entry.identity, entry.deviceClass, entry.rssi, now);
        final YoCbtDiscoveryResult result = new YoCbtDiscoveryResult(snapshot, now);

        runtime.callback(() -> session.observer.onDeviceFound(result));
        publishDiscoveryEvent(new YoCbtDiscoveryEvent.DeviceFoundEvent(observed, !isNew, now));

        YoBtLogger.d(TAG, "发现设备: " + identity.getDisplayName()
                + " [" + deviceId + "] 信号强度=" + rssi
                + (isNew ? "（新设备）" : "（已更新）"));
    }

    private void initiateDiscovery(@NonNull YoCbtDiscoveryOperationContext session) {
        if (session.completed.get()) {
            state.set(YoCbtDiscoveryState.IDLE);
            return;
        }

        try {
            // 申请 CLASSIC_DISCOVERY 租约，自动协调停止/挂起 BLE 扫描
            YoBtRadioLease lease = radioCoordinator.acquireLease(
                    YoBtRadioLease.Type.CLASSIC_DISCOVERY, "YoCbtDiscoveryController");
            if (lease == null) {
                failDiscovery(session, YoCbtErrorCode.YO_CBT_DISCOVERY_START_FAILED,
                        "RadioCoordinator rejected CLASSIC_DISCOVERY lease (radio preempted for connection)");
                return;
            }
            this.activeRadioLease = lease;

            session.scope = runtime.createScope();
            discoveryBroadcastPort.setListener(new YoCbtDiscoveryBroadcastPort.Listener() {
                @Override
                public void onDiscoveryStarted() {
                    YoCbtDiscoveryController.this.onDiscoveryStarted();
                }

                @Override
                public void onDiscoveryFinished() {
                    YoCbtDiscoveryController.this.onDiscoveryFinished();
                }

                @Override
                public void onDeviceFound(@NonNull YoCbtDiscoveryDeviceFound found) {
                    YoCbtDiscoveryController.this.onDeviceFound(found);
                }
            });

            state.set(YoCbtDiscoveryState.DISCOVERING);
            session.eventSession.setState(YoCbtDiscoveryState.DISCOVERING);
            notifyStateChanged(session, YoCbtDiscoveryState.DISCOVERING);
            publishDiscoveryEvent(new YoCbtDiscoveryEvent.DiscoveryStartedEvent(
                    session.eventSession, System.currentTimeMillis()));

            if (!discoveryPlatform.startDiscovery()) {
                // 前一次 cancelDiscovery 尚未在协议栈落定时，系统会瞬时返回 false。
                // 此处不阻塞等待：交由运行时调度器做一次条件重试（回调到期后回 state 线程重新判定）。
                scheduleStartRetry(session);
            }

            session.timeoutHandle = runtime.scheduler().schedule(() -> {
                runtime.state(() -> {
                    YoBtLogger.d(TAG, "发现超时自然结束：操作=" + session.operationId);
                    completeDiscoveryNormally(session);
                });
            }, session.config.getTimeoutMillis());

            YoBtLogger.d(TAG, "发现已发起：操作=" + session.operationId
                    + "，超时=" + session.config.getTimeoutMillis() + "毫秒");
        } catch (Throwable t) {
            failDiscovery(session, YoCbtErrorCode.YO_CBT_DISCOVERY_START_FAILED,
                    "Exception during discovery initiation", t);
        }
    }

    /**
     * 系统在前一次 cancelDiscovery 尚未落定时会瞬时返回 {@code false}。
     *
     * <p>此前以固定 80ms 休眠在状态线程上原地等待，会阻塞整条状态队列；
     * 这里改为运行时调度器触发一次条件重试：回调到期后回到 state 线程，重新判定会话是否仍有效，
     * 再决定发起或失败。仅一次、有界，不轮询。</p>
     */
    private void scheduleStartRetry(@NonNull YoCbtDiscoveryOperationContext session) {
        cancelRetry(session);
        YoBtLogger.d(TAG, "startDiscovery 返回 false，安排一次条件重试：操作=" + session.operationId);
        session.retryHandle = runtime.scheduler().schedule(() -> runtime.state(() -> {
            session.retryHandle = null;
            if (session.completed.get()) {
                return;
            }
            if (discoveryPlatform.startDiscovery()) {
                YoBtLogger.d(TAG, "条件重试后发现已启动：操作=" + session.operationId);
                return;
            }
            failDiscovery(session, YoCbtErrorCode.YO_CBT_DISCOVERY_START_FAILED,
                    "BluetoothAdapter.startDiscovery() returned false");
        }), DISCOVERY_START_RETRY_DELAY_MILLIS);
    }

    private void cancelRetry(@Nullable YoCbtDiscoveryOperationContext session) {
        if (session == null || session.retryHandle == null) {
            return;
        }
        session.retryHandle.cancel();
        session.retryHandle = null;
    }

    private void stopDiscoveryInternal(@NonNull YoCbtDiscoveryOperationContext session) {
        if (!session.completed.compareAndSet(false, true)) return;

        cancelRetry(session);

        if (session.timeoutHandle != null) {
            session.timeoutHandle.cancel();
            session.timeoutHandle = null;
        }

        releaseRadioLease();
        discoveryPlatform.cancelDiscovery();
        clearReceiverListener();
        activeSession.compareAndSet(session, null);
        state.set(YoCbtDiscoveryState.IDLE);
        notifyStateChanged(session, YoCbtDiscoveryState.IDLE);
        YoBtLogger.d(TAG, "发现已停止：操作=" + session.operationId);
    }

    private void failDiscovery(@NonNull YoCbtDiscoveryOperationContext session,
                               @NonNull YoCbtErrorCode errorCode,
                               @NonNull String message) {
        failDiscovery(session, errorCode, message, null);
    }

    private void failDiscovery(@NonNull YoCbtDiscoveryOperationContext session,
                               @NonNull YoCbtErrorCode errorCode,
                               @NonNull String message,
                               @Nullable Throwable cause) {
        cleanupSession(session);

        final YoCbtFailure failure = cause != null
                ? new YoCbtFailure(errorCode, message, cause, true, false,
                SOURCE_MODULE, null, null, null, null, "")
                : YoCbtFailure.of(errorCode, message, SOURCE_MODULE);

        state.set(YoCbtDiscoveryState.FAILED);
        notifyStateChanged(session, YoCbtDiscoveryState.FAILED);
        runtime.callback(() -> session.observer.onDiscoveryFailed(failure));
        publishDiscoveryEvent(new YoCbtDiscoveryEvent.DiscoveryFailedEvent(
                session.eventSession,
                YoCbtDiscoveryFailure.of(errorCode, message, SOURCE_MODULE),
                System.currentTimeMillis()));

        state.set(YoCbtDiscoveryState.IDLE);
        String detail = "发现失败：操作=" + session.operationId
                + "，错误码=" + errorCode + "，信息=" + message;
        if (cause != null) {
            runtime.diagnostics().recordException(TAG, detail, cause);
        } else {
            YoBtLogger.e(TAG, detail);
        }
    }

    private void cleanupSession(@NonNull YoCbtDiscoveryOperationContext session) {
        session.completed.set(true);
        cancelRetry(session);
        releaseSessionScope(session);
        if (session.timeoutHandle != null) {
            session.timeoutHandle.cancel();
            session.timeoutHandle = null;
        }
        releaseRadioLease();
        discoveryPlatform.cancelDiscovery();
        clearReceiverListener();
        activeSession.compareAndSet(session, null);
    }

    private void releaseRadioLease() {
        YoBtRadioLease lease = activeRadioLease;
        activeRadioLease = null;
        if (lease != null) {
            lease.release();
        }
    }

    private void clearReceiverListener() {
        discoveryBroadcastPort.setListener(null);
    }

    private void notifyStateChanged(@NonNull YoCbtDiscoveryOperationContext session,
                                    @NonNull YoCbtDiscoveryState newState) {
        session.eventSession.setState(newState);
        runtime.callback(() -> session.observer.onDiscoveryStateChanged(newState));
    }

    private void publishDiscoveryEvent(@NonNull YoCbtDiscoveryEvent event) {
        runtime.eventBus().publish(YoCbtEvents.DISCOVERY, event);
    }

    private void releaseSessionScope(@Nullable YoCbtDiscoveryOperationContext session) {
        if (session == null || session.scope == null) return;
        session.scope.release();
        session.scope = null;
    }

    private static YoCbtDeviceSnapshot buildSnapshot(@NonNull YoCbtDeviceIdentity identity,
                                                     @NonNull YoCbtDeviceClass deviceClass,
                                                     int rssi,
                                                     long timestampMillis) {
        return new YoCbtDeviceSnapshot(
                identity,
                identity.getAlias(),
                deviceClass,
                YoCbtDeviceCapability.empty(),
                YoCbtBondState.UNKNOWN,
                YoCbtAclLinkState.UNKNOWN,
                Collections.emptyList(),
                new EnumMap<>(YoCbtProfileType.class),
                null,
                null,
                Collections.emptyList(),
                rssi,
                timestampMillis,
                0L,
                timestampMillis,
                YoCbtCapabilitySource.UNKNOWN);
    }
}
