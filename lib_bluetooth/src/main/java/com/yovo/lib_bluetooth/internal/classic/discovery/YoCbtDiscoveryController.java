package com.yovo.lib_bluetooth.internal.classic.discovery;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.common.concurrency.YoTimeoutHandle;
import com.yovo.lib_bluetooth.internal.common.diagnostics.YoBluetoothLogger;
import com.yovo.lib_bluetooth.internal.common.permission.YoBluetoothPermission;
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
import com.yovo.lib_bluetooth.internal.classic.runtime.YoCbtRuntimeScope;

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

    private final Context appContext;
    private final YoCbtRuntime runtime;
    private final YoBluetoothPermissionChecker permissionChecker;
    private final YoCbtDiscoveryPlatform discoveryPlatform;
    private final YoCbtDiscoveryBroadcastPort discoveryBroadcastPort;
    @Nullable
    private final YoCbtDiscoveryResultMerger resultMerger;

    private final AtomicReference<YoCbtDiscoveryState> state =
            new AtomicReference<>(YoCbtDiscoveryState.IDLE);
    private final AtomicReference<YoCbtDiscoveryOperationContext> activeSession = new AtomicReference<>(null);

    public YoCbtDiscoveryController(@NonNull Context appContext,
                                    @NonNull YoCbtRuntime runtime,
                                    @NonNull YoBluetoothPermissionChecker permissionChecker,
                                    @NonNull YoCbtDiscoveryPlatform discoveryPlatform,
                                    @NonNull YoCbtDiscoveryBroadcastPort discoveryBroadcastPort,
                                    @Nullable YoCbtDiscoveryResultMerger resultMerger) {
        this.appContext = appContext.getApplicationContext();
        this.runtime = runtime;
        this.permissionChecker = permissionChecker;
        this.discoveryPlatform = discoveryPlatform;
        this.discoveryBroadcastPort = discoveryBroadcastPort;
        this.resultMerger = resultMerger;
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
        if (discoveryPlatform.requiresLegacyLocationPermission()
                && !discoveryPlatform.hasLegacyLocationPermission(appContext)) {
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

        runtime.state(() -> {
            YoCbtDiscoveryOperationContext s = activeSession.get();
            if (s == null || s.completed.get()) return;
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
        releaseSessionScope(session);
        if (session.timeoutHandle != null) {
            session.timeoutHandle.cancel();
            session.timeoutHandle = null;
        }

        discoveryPlatform.cancelDiscovery();
        clearReceiverListener();
        state.set(YoCbtDiscoveryState.IDLE);
        YoBluetoothLogger.d(TAG, "cancelForConnect：发现流程已取消");
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
            if (session.timeoutHandle != null) {
                session.timeoutHandle.cancel();
                session.timeoutHandle = null;
            }
        }

        discoveryPlatform.cancelDiscovery();
        clearReceiverListener();
        YoBluetoothLogger.d(TAG, "发现控制器已释放");
    }

    private void onDiscoveryStarted() {
        YoCbtDiscoveryOperationContext session = activeSession.get();
        if (session == null || session.completed.get()) return;
        YoBluetoothLogger.d(TAG, "收到 ACTION_DISCOVERY_STARTED：操作=" + session.operationId);
    }

    private void onDiscoveryFinished() {
        YoCbtDiscoveryOperationContext session = activeSession.get();
        if (session == null) return;
        if (!session.completed.compareAndSet(false, true)) return;

        if (session.timeoutHandle != null) {
            session.timeoutHandle.cancel();
            session.timeoutHandle = null;
        }

        clearReceiverListener();
        activeSession.compareAndSet(session, null);

        state.set(YoCbtDiscoveryState.FINISHED);
        notifyStateChanged(session, YoCbtDiscoveryState.FINISHED);
        state.set(YoCbtDiscoveryState.IDLE);

        YoBluetoothLogger.d(TAG, "发现完成：操作=" + session.operationId
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

        YoBluetoothLogger.d(TAG, "发现设备: " + identity.getDisplayName()
                + " [" + deviceId + "] 信号强度=" + rssi
                + (isNew ? "（新设备）" : "（已更新）"));
    }

    private void initiateDiscovery(@NonNull YoCbtDiscoveryOperationContext session) {
        if (session.completed.get()) return;

        try {
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
                failDiscovery(session, YoCbtErrorCode.YO_CBT_DISCOVERY_START_FAILED,
                        "BluetoothAdapter.startDiscovery() returned false");
                return;
            }

            session.timeoutHandle = runtime.scheduler().schedule(() -> {
                runtime.state(() -> {
                    if (!session.completed.compareAndSet(false, true)) return;
                    if (session.timeoutHandle != null) {
                        session.timeoutHandle.cancel();
                        session.timeoutHandle = null;
                    }
                    discoveryPlatform.cancelDiscovery();
                    clearReceiverListener();
                    activeSession.compareAndSet(session, null);
                    failDiscovery(session, YoCbtErrorCode.YO_CBT_DISCOVERY_TIMEOUT,
                            "Discovery timed out after "
                                    + session.config.getTimeoutMillis() + "ms");
                });
            }, session.config.getTimeoutMillis());

            YoBluetoothLogger.d(TAG, "发现已启动：操作=" + session.operationId
                    + "，超时=" + session.config.getTimeoutMillis() + "毫秒");
        } catch (Throwable t) {
            YoBluetoothLogger.e(TAG, "启动发现时发生异常", t);
            failDiscovery(session, YoCbtErrorCode.YO_CBT_DISCOVERY_START_FAILED,
                    "Exception during discovery initiation", t);
        }
    }

    private void stopDiscoveryInternal(@NonNull YoCbtDiscoveryOperationContext session) {
        if (!session.completed.compareAndSet(false, true)) return;

        if (session.timeoutHandle != null) {
            session.timeoutHandle.cancel();
            session.timeoutHandle = null;
        }

        discoveryPlatform.cancelDiscovery();
        clearReceiverListener();
        activeSession.compareAndSet(session, null);
        state.set(YoCbtDiscoveryState.IDLE);
        notifyStateChanged(session, YoCbtDiscoveryState.IDLE);
        YoBluetoothLogger.d(TAG, "发现已停止：操作=" + session.operationId);
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
        YoBluetoothLogger.e(TAG, "发现失败：操作=" + session.operationId
                + "，错误码=" + errorCode + "，信息=" + message, cause);
    }

    private void cleanupSession(@NonNull YoCbtDiscoveryOperationContext session) {
        session.completed.set(true);
        releaseSessionScope(session);
        if (session.timeoutHandle != null) {
            session.timeoutHandle.cancel();
            session.timeoutHandle = null;
        }
        discoveryPlatform.cancelDiscovery();
        clearReceiverListener();
        activeSession.compareAndSet(session, null);
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
        runtime.eventHub().publish(YoCbtEvents.DISCOVERY, event);
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
