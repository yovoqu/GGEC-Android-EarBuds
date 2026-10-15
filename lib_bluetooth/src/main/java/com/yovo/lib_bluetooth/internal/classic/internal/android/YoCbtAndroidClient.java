package com.yovo.lib_bluetooth.internal.classic.internal.android;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.common.diagnostics.YoBluetoothLogger;
import com.yovo.lib_bluetooth.internal.common.permission.YoBluetoothPermission;
import com.yovo.lib_bluetooth.internal.common.result.YoResult;
import com.yovo.lib_bluetooth.internal.classic.api.CbtInternalClient;
import com.yovo.lib_bluetooth.internal.classic.api.CbtInternalClientConfig;
import com.yovo.lib_bluetooth.internal.classic.api.CbtInternalOperationHandle;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceIdentity;
import com.yovo.lib_bluetooth.internal.classic.device.snapshot.YoCbtConnectedDeviceObserver;
import com.yovo.lib_bluetooth.internal.classic.device.snapshot.YoCbtDeviceSnapshot;
import com.yovo.lib_bluetooth.internal.classic.discovery.YoCbtDiscoveryConfig;
import com.yovo.lib_bluetooth.internal.classic.discovery.YoCbtDiscoveryObserver;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtErrorCode;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtFailure;
import com.yovo.lib_bluetooth.internal.classic.link.YoCbtAclLinkObserver;
import com.yovo.lib_bluetooth.internal.classic.link.YoCbtAclLinkState;
import com.yovo.lib_bluetooth.internal.classic.pairing.YoCbtBondStateObserver;
import com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtDeviceProfileSnapshot;
import com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtDeviceProfileState;
import com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtProfileAggregateState;
import com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtProfileStateObserver;
import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtProfileType;
import com.yovo.lib_bluetooth.internal.classic.protocol.command.YoCbtCommand;
import com.yovo.lib_bluetooth.internal.classic.protocol.command.YoCbtCommandObserver;
import com.yovo.lib_bluetooth.internal.classic.protocol.command.YoCbtCommandRequest;
import com.yovo.lib_bluetooth.internal.classic.protocol.command.YoCbtCommandResult;
import com.yovo.lib_bluetooth.internal.classic.protocol.command.YoCbtCommandSession;
import com.yovo.lib_bluetooth.internal.classic.protocol.command.YoCbtCommandSessionRegistry;
import com.yovo.lib_bluetooth.internal.classic.runtime.YoCbtEvents;
import com.yovo.lib_bluetooth.internal.classic.service.discovery.YoCbtServiceDiscoveryConfig;
import com.yovo.lib_bluetooth.internal.classic.service.discovery.YoCbtServiceDiscoveryObserver;
import com.yovo.lib_bluetooth.internal.classic.service.model.YoCbtSdpResult;
import com.yovo.lib_bluetooth.internal.classic.service.model.YoCbtServiceRecord;
import com.yovo.lib_bluetooth.internal.classic.spp.connection.YoCbtSppConfig;
import com.yovo.lib_bluetooth.internal.classic.spp.connection.YoCbtSppConnectionId;
import com.yovo.lib_bluetooth.internal.classic.spp.connection.YoCbtSppConnectionObserver;
import com.yovo.lib_bluetooth.internal.classic.spp.connection.YoCbtSppConnectionSnapshot;
import com.yovo.lib_bluetooth.internal.classic.spp.connection.YoCbtSppConnectionState;
import com.yovo.lib_bluetooth.internal.classic.spp.endpoint.YoCbtSppEndpoint;
import com.yovo.lib_bluetooth.internal.classic.spp.connection.YoCbtSppStateObserver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description {@link CbtInternalClient} 的 Android 实现，委托 {@link YoCbtAndroidModuleGraph} 各能力模块。
 */
public final class YoCbtAndroidClient implements CbtInternalClient {

    private static final String TAG = "YoCbtAndroidClient";

    private final YoCbtAndroidModuleGraph modules;

    private final CopyOnWriteArrayList<YoCbtBondStateObserver> bondObservers = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<YoCbtAclLinkObserver> aclObservers = new CopyOnWriteArrayList<>();
    private final Map<YoCbtDeviceId, CopyOnWriteArrayList<YoCbtProfileStateObserver>> profileObservers =
            new HashMap<>();
    private final CopyOnWriteArrayList<YoCbtProfileStateObserver> globalProfileObservers =
            new CopyOnWriteArrayList<>();

    /**
     * 学习到的 UUID -> 物理 RFCOMM 通道映射，键为 {@code deviceAddress|uuid}。
     * Android 的 SDP（getUuids）通常不返回 RFCOMM 通道，故 UUID 连接成功后通过 mPort 反推到的
     * 通道在此缓存，供后续连接做跨端点冲突检测（例如已连原始通道，再以对应 UUID 连接时可拦截）。
     */
    private final Map<String, Integer> learnedSppChannels = new ConcurrentHashMap<>();

    private final AtomicLong operationSeq = new AtomicLong();

    private boolean released;

    public YoCbtAndroidClient(@NonNull Context context, @NonNull CbtInternalClientConfig clientConfig) {
        this.modules = YoCbtAndroidModuleGraph.create(context, clientConfig, this::collectProfileObservers);
        YoBluetoothLogger.i(TAG, "YoCbtAndroidClient 已创建，SDK=" + android.os.Build.VERSION.SDK_INT);

        modules.bondStateReceiver.addObserver(snapshot -> {
            for (YoCbtBondStateObserver o : bondObservers) o.onBondStateChanged(snapshot);
        });
        modules.aclReceiver.addObserver(snapshot -> {
            for (YoCbtAclLinkObserver o : aclObservers) o.onAclLinkChanged(snapshot);
        });

        wireEventSubscriptions();
        modules.registerReceivers();
    }

    private void wireEventSubscriptions() {
        modules.runtime.eventHub().subscribe(YoCbtEvents.ACL_LINK_CHANGED, payload -> {
            if (!(payload instanceof YoCbtDeviceSnapshot)) return;
            YoCbtDeviceSnapshot snapshot = (YoCbtDeviceSnapshot) payload;
            if (snapshot.getAclLinkState() != YoCbtAclLinkState.DISCONNECTED) return;
            YoCbtDeviceId deviceId = snapshot.getIdentity().getDeviceId();
            modules.runtime.state(() -> {
                modules.sppManager.onAclDisconnected(deviceId);
                List<YoCbtDeviceProfileState> changed =
                        modules.profileStateRepository.refreshOnAclDisconnected(deviceId);
                for (YoCbtDeviceProfileState state : changed) {
                    notifyProfileStateChanged(state);
                }
            });
        });
    }

    // ── Discovery ────────────────────────────────────────────────

    @Override
    public CbtInternalOperationHandle discover(@NonNull YoCbtDiscoveryConfig config,
                                          @NonNull YoCbtDiscoveryObserver observer) {
        if (released) {
            YoBluetoothLogger.w(TAG, "客户端已释放，无法开始发现");
            modules.runtime.callback(() -> observer.onDiscoveryFailed(
                    failure(YoCbtErrorCode.YO_CBT_RELEASED, "Classic client released", null)));
            return new CbtInternalOperationHandle(nextOperationId("discovery"), () -> {});
        }
        String id = nextOperationId("discovery");
        YoBluetoothLogger.i(TAG, "开始经典蓝牙发现，操作=" + id);
        YoResult<Void> started = modules.discoveryController.start(config, observer);
        if (started.isFailure()) {
            YoBluetoothLogger.w(TAG, "经典蓝牙发现启动失败");
            YoCbtFailure discoveryFailure = (YoCbtFailure) started.getFailure();
            modules.runtime.callback(() -> observer.onDiscoveryFailed(discoveryFailure));
            return new CbtInternalOperationHandle(id, () -> {});
        }
        YoBluetoothLogger.d(TAG, "经典蓝牙发现已启动，操作=" + id);
        return new CbtInternalOperationHandle(id, modules.discoveryController::stop);
    }

    @Override
    public YoResult<Void> stopDiscovery() {
        return modules.discoveryController.stop();
    }

    // ── Pairing / Bond ───────────────────────────────────────────

    @Override
    public YoResult<List<YoCbtDeviceSnapshot>> getBondedDevices() {
        return modules.bondedDeviceProvider.getBondedDevices(modules.appContext);
    }

    @Override
    public YoResult<Void> createBond(@NonNull YoCbtDeviceId deviceId) {
        YoBluetoothLogger.i(TAG, "发起配对，设备=" + deviceId.value());
        return modules.bondOperationController.createBond(modules.appContext, deviceId);
    }

    @Override
    public YoResult<Void> cancelBondProcess(@NonNull YoCbtDeviceId deviceId) {
        YoBluetoothLogger.i(TAG, "取消配对流程，设备=" + deviceId.value());
        return modules.bondOperationController.cancelBondProcess(deviceId);
    }

    @Override
    public YoResult<Void> removeBond(@NonNull YoCbtDeviceId deviceId) {
        YoBluetoothLogger.i(TAG, "解除配对，设备=" + deviceId.value());
        return modules.bondOperationController.removeBond(deviceId);
    }

    @Override
    public CbtInternalOperationHandle observeBondState(@NonNull YoCbtBondStateObserver observer) {
        bondObservers.addIfAbsent(observer);
        return new CbtInternalOperationHandle(nextOperationId("bond"), () -> bondObservers.remove(observer));
    }

    // ── ACL Link ─────────────────────────────────────────────────

    @Override
    public CbtInternalOperationHandle observeAclLinkState(@NonNull YoCbtAclLinkObserver observer) {
        aclObservers.addIfAbsent(observer);
        return new CbtInternalOperationHandle(nextOperationId("acl"), () -> aclObservers.remove(observer));
    }

    @Override
    public YoResult<YoCbtAclLinkState> getAclState(@NonNull YoCbtDeviceId deviceId) {
        if (released) {
            return YoResult.failure(failure(YoCbtErrorCode.YO_CBT_RELEASED,
                    "Classic client released", null));
        }
        return YoResult.success(modules.aclLinkRepository.getAclState(deviceId));
    }

    @Override
    public YoResult<List<YoCbtDeviceId>> getAclConnectedDevices() {
        if (released) {
            return YoResult.failure(failure(YoCbtErrorCode.YO_CBT_RELEASED,
                    "Classic client released", null));
        }
        return YoResult.success(modules.aclLinkRepository.getConnectedDevices());
    }

    @Override
    public CbtInternalOperationHandle queryConnectedDevices(@NonNull YoCbtConnectedDeviceObserver observer) {
        if (released) {
            YoCbtFailure releasedFailure = failure(YoCbtErrorCode.YO_CBT_RELEASED,
                    "Classic client released", null);
            modules.runtime.callback(() -> observer.onConnectedDeviceQueryFailed(releasedFailure));
            return new CbtInternalOperationHandle(nextOperationId("connected-devices"), () -> {});
        }
        return modules.connectedDeviceQuery.query(new YoCbtConnectedDeviceObserver() {
            @Override
            public void onConnectedDevices(@NonNull List<YoCbtDeviceSnapshot> snapshots) {
                observer.onConnectedDevices(mergeConnectedSppDevices(snapshots));
            }

            @Override
            public void onConnectedDeviceQueryFailed(@NonNull YoCbtFailure failure) {
                observer.onConnectedDeviceQueryFailed(failure);
            }
        });
    }

    // ── SDP Service Discovery ────────────────────────────────────

    @Override
    public CbtInternalOperationHandle discoverServices(@NonNull YoCbtDeviceId deviceId,
                                                  @NonNull YoCbtServiceDiscoveryConfig config,
                                                  @NonNull YoCbtServiceDiscoveryObserver observer) {
        if (released) {
            YoBluetoothLogger.w(TAG, "客户端已释放，无法执行 SDP 服务发现");
            modules.runtime.callback(() -> observer.onServiceDiscoveryFailed(
                    failure(YoCbtErrorCode.YO_CBT_RELEASED, "Classic client released", null)));
            return new CbtInternalOperationHandle(nextOperationId("service"), () -> {});
        }
        YoBluetoothLogger.i(TAG, "开始 SDP 服务发现，设备=" + deviceId.value());
        return modules.serviceDiscoveryClient.discover(deviceId, config, observer);
    }

    // ── Profiles ─────────────────────────────────────────────────

    @Override
    public YoResult<Void> prepareProfileProxy(@NonNull YoCbtProfileType profileType) {
        if (profileType == YoCbtProfileType.SPP) {
            return YoResult.success(null);
        }
        if (profileType.isExtensionOnly()) {
            if (!modules.profileExtensionRegistry.isSupported(profileType)) {
                return YoResult.failure(failure(YoCbtErrorCode.YO_CBT_PROFILE_TYPE_UNSUPPORTED,
                        "Profile extension not supported: " + profileType, null));
            }
            return YoResult.success(null);
        }
        if (!profileType.hasAndroidProfile()) {
            return YoResult.failure(failure(YoCbtErrorCode.YO_CBT_PROFILE_TYPE_UNSUPPORTED,
                    "Profile has no independent Android connection state: " + profileType, null));
        }
        return modules.profileProxyManager.acquireProxy(profileType);
    }

    @Override
    public YoResult<YoCbtProfileAggregateState> getProfileAggregateState(
            @NonNull YoCbtProfileType profileType) {
        if (released) {
            return YoResult.failure(failure(YoCbtErrorCode.YO_CBT_RELEASED,
                    "Classic client released", null));
        }
        if (profileType == YoCbtProfileType.SPP) {
            int connectedCount = countConnectedSppDevices();
            return YoResult.success(new YoCbtProfileAggregateState(
                    YoCbtProfileType.SPP,
                    connectedCount > 0
                            ? com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtProfileConnectionState.CONNECTED
                            : com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtProfileConnectionState.DISCONNECTED,
                    connectedCount,
                    System.currentTimeMillis()));
        }
        if (profileType.isExtensionOnly()) {
            return YoResult.success(new YoCbtProfileAggregateState(
                    profileType,
                    com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtProfileConnectionState.UNKNOWN,
                    0,
                    System.currentTimeMillis()));
        }
        BluetoothProfile proxy = modules.profileProxyManager.getProxy(profileType);
        return modules.profileStateQuerier.getAggregateState(profileType,
                modules.adapterProvider.adapter(), proxy);
    }

    @Override
    public YoResult<YoCbtDeviceProfileState> getDeviceProfileState(
            @NonNull YoCbtDeviceId deviceId, @NonNull YoCbtProfileType profileType) {
        if (released) {
            return YoResult.failure(failure(YoCbtErrorCode.YO_CBT_RELEASED,
                    "Classic client released", null));
        }
        if (profileType == YoCbtProfileType.SPP) {
            syncSppProfileState(deviceId);
            YoCbtDeviceSnapshot snapshot = modules.snapshotStore.get(deviceId);
            YoCbtDeviceProfileState state = snapshot != null
                    ? snapshot.getProfileStates().get(YoCbtProfileType.SPP)
                    : null;
            if (state == null) {
                return YoResult.failure(failure(YoCbtErrorCode.YO_CBT_PROFILE_STATE_QUERY_FAILED,
                        "Device not found in SPP connection registry: " + deviceId, null));
            }
            return YoResult.success(state);
        }
        if (!profileType.isExtensionOnly() && !profileType.hasAndroidProfile()) {
            return YoResult.failure(failure(YoCbtErrorCode.YO_CBT_PROFILE_TYPE_UNSUPPORTED,
                    "Profile has no independent Android connection state: " + profileType, null));
        }
        BluetoothProfile proxy = profileType.isExtensionOnly()
                ? null
                : modules.profileProxyManager.getProxy(profileType);
        return modules.profileStateQuerier.getDeviceProfileState(
                modules.appContext, deviceId, profileType, modules.adapterProvider.adapter(), proxy);
    }

    @Override
    public YoResult<YoCbtDeviceProfileSnapshot> getDeviceProfileSnapshot(
            @NonNull YoCbtDeviceId deviceId) {
        return modules.profileStateRepository.getDeviceProfileSnapshot(deviceId);
    }

    @Override
    public YoResult<List<YoCbtProfileType>> getConnectedProfiles(@NonNull YoCbtDeviceId deviceId) {
        return modules.profileStateRepository.getConnectedProfiles(deviceId);
    }

    @Override
    public CbtInternalOperationHandle observeProfileState(@NonNull YoCbtDeviceId deviceId,
                                                     @NonNull YoCbtProfileStateObserver observer) {
        synchronized (profileObservers) {
            profileObservers.computeIfAbsent(deviceId,
                    k -> new CopyOnWriteArrayList<>()).addIfAbsent(observer);
        }
        return new CbtInternalOperationHandle(nextOperationId("profile"), () -> {
            synchronized (profileObservers) {
                List<YoCbtProfileStateObserver> observers = profileObservers.get(deviceId);
                if (observers != null) observers.remove(observer);
            }
        });
    }

    @Override
    public CbtInternalOperationHandle observeProfileState(@NonNull YoCbtProfileStateObserver observer) {
        globalProfileObservers.addIfAbsent(observer);
        return new CbtInternalOperationHandle(nextOperationId("profile-global"),
                () -> globalProfileObservers.remove(observer));
    }

    // ── SPP ──────────────────────────────────────────────────────

    @Override
    public CbtInternalOperationHandle connectSpp(@NonNull YoCbtDeviceId deviceId,
                                           @NonNull YoCbtSppEndpoint endpoint,
                                           @NonNull YoCbtSppConfig config,
                                           @NonNull YoCbtSppConnectionObserver observer) {
        String opId = nextOperationId("spp");
        if (released) {
            YoBluetoothLogger.w(TAG, "客户端已释放，无法连接 SPP，设备=" + deviceId.value());
            return new CbtInternalOperationHandle(opId, () -> {});
        }

        YoCbtErrorCode permissionDenied = sppConnectPermissionDenied();
        if (permissionDenied != null) {
            YoBluetoothLogger.w(TAG, "SPP 连接缺少权限 " + permissionDenied
                    + "，设备=" + deviceId.value());
            failSppStart(opId, deviceId, endpoint, observer, new YoCbtFailure(
                    permissionDenied,
                    permissionDenied == YoCbtErrorCode.YO_CBT_PERMISSION_SCAN_DENIED
                            ? "BLUETOOTH_SCAN permission is required before SPP connect"
                            : "BLUETOOTH_CONNECT permission is required to open an SPP socket",
                    null, false, false, TAG,
                    null, null, null, endpoint, ""));
            return new CbtInternalOperationHandle(opId, () -> {});
        }

        YoBluetoothLogger.i(TAG, "开始 SPP 连接，设备=" + deviceId.value()
                + "，端点=" + endpoint + "，操作=" + opId);
        modules.discoveryController.cancelForConnect();

        YoCbtSppConfig enriched = enrichSppConfig(config);
        YoResult<YoCbtSppConnectionId> connectResult = modules.sppManager.connect(
                deviceId, endpoint, enriched, wrapSppObserver(deviceId, observer),
                resolveSdpEndpoints(deviceId), resolveRequestedChannel(deviceId, endpoint));

        if (connectResult.isFailure()) {
            YoBluetoothLogger.w(TAG, "SPP 连接启动失败，设备=" + deviceId.value()
                    + "，端点=" + endpoint);
            failSppStart(opId, deviceId, endpoint, observer,
                    (YoCbtFailure) connectResult.getFailure());
            return new CbtInternalOperationHandle(opId, () -> {});
        }

        YoCbtSppConnectionId connectionId = connectResult.getValue();
        YoBluetoothLogger.i(TAG, "SPP 连接已发起，连接 ID=" + connectionId);
        return new CbtInternalOperationHandle(opId, () -> {
            removeCommandSession(connectionId);
            modules.sppManager.cancelConnect(connectionId);
        });
    }

    @Nullable
    private YoCbtErrorCode sppConnectPermissionDenied() {
        if (!modules.permissionChecker.has(modules.appContext, YoBluetoothPermission.CONNECT)) {
            return YoCbtErrorCode.YO_CBT_PERMISSION_CONNECT_DENIED;
        }
        // Android 12+：cancelDiscovery() 与 connect() 内的 SDP 需要 BLUETOOTH_SCAN。
        // 更早版本同一检查对应安装时 BLUETOOTH / BLUETOOTH_ADMIN，不包含定位。
        if (!modules.permissionChecker.has(modules.appContext, YoBluetoothPermission.SCAN)) {
            return YoCbtErrorCode.YO_CBT_PERMISSION_SCAN_DENIED;
        }
        return null;
    }

    private void failSppStart(@NonNull String opId,
                              @NonNull YoCbtDeviceId deviceId,
                              @NonNull YoCbtSppEndpoint endpoint,
                              @NonNull YoCbtSppConnectionObserver observer,
                              @NonNull YoCbtFailure connectFailure) {
        YoCbtSppConnectionId failedId = new YoCbtSppConnectionId(opId);
        int channel = resolveRequestedChannel(deviceId, endpoint);
        YoCbtSppConnectionSnapshot failedSnapshot = new YoCbtSppConnectionSnapshot(
                failedId,
                deviceId,
                endpoint,
                YoCbtSppConnectionState.FAILED,
                System.currentTimeMillis(),
                connectFailure,
                channel);
        modules.runtime.callback(() -> observer.onSppStateChanged(failedSnapshot));
    }

    @NonNull
    private YoCbtSppConfig enrichSppConfig(@NonNull YoCbtSppConfig config) {
        return YoCbtSppConfig.builder()
                .connectTimeoutMillis(config.getConnectTimeoutMillis())
                .maxConnectAttempts(config.getMaxConnectAttempts())
                .retryDelayMillis(config.getRetryDelayMillis())
                .allowHiddenApiChannel(config.isAllowHiddenApiChannel()
                        || modules.clientConfig.isAllowHiddenApiSppChannel())
                .connectionPolicy(config.getConnectionPolicy())
                .writeQueueCapacity(modules.clientConfig.getSppWriteQueueCapacity() > 0
                        ? modules.clientConfig.getSppWriteQueueCapacity()
                        : config.getWriteQueueCapacity())
                .build();
    }

    @NonNull
    private YoCbtSppConnectionObserver wrapSppObserver(@NonNull YoCbtDeviceId deviceId,
                                                       @NonNull YoCbtSppConnectionObserver observer) {
        return new YoCbtSppConnectionObserver() {
            @Override
            public void onSppDeviceResolved(@NonNull YoCbtDeviceIdentity identity) {
                modules.snapshotStore.updateObserved(identity, Integer.MIN_VALUE, System.currentTimeMillis());
                observer.onSppDeviceResolved(identity);
            }

            @Override
            public void onSppStateChanged(@NonNull YoCbtSppConnectionSnapshot snapshot) {
                modules.snapshotStore.replaceSppSnapshots(deviceId, modules.sppManager.snapshots(deviceId),
                        System.currentTimeMillis());
                syncSppProfileState(deviceId);
                if (snapshot.getState() == YoCbtSppConnectionState.CONNECTED) {
                    YoCbtSppConnectionId cid = snapshot.getConnectionId();
                    learnSppChannel(deviceId, snapshot);
                    modules.commandSessionRegistry.getOrCreate(cid, bytes -> send(cid, bytes));
                }
                observer.onSppStateChanged(snapshot);
            }

            @Override
            public void onSppDataReceived(@NonNull YoCbtSppConnectionId cid, @NonNull byte[] data) {
                YoCbtCommandSession session = modules.commandSessionRegistry.get(cid);
                if (session != null) session.onBytesReceived(data);
                observer.onSppDataReceived(cid, data);
            }

            @Override
            public void onSppDataSent(@NonNull YoCbtSppConnectionId cid, @NonNull byte[] data) {
                observer.onSppDataSent(cid, data);
            }
        };
    }

    @Override
    public YoResult<Void> disconnectSpp(@NonNull YoCbtSppConnectionId connectionId) {
        YoBluetoothLogger.i(TAG, "断开 SPP 连接，连接 ID=" + connectionId);
        removeCommandSession(connectionId);
        return modules.sppManager.disconnect(connectionId);
    }

    @Override
    public YoResult<Void> disconnectSppByDevice(@NonNull YoCbtDeviceId deviceId) {
        YoBluetoothLogger.i(TAG, "按设备断开 SPP 连接，设备=" + deviceId.value());
        YoResult<List<YoCbtSppConnectionSnapshot>> snapshots = getSppConnections(deviceId);
        if (snapshots.isSuccess() && snapshots.getValue() != null) {
            for (YoCbtSppConnectionSnapshot s : snapshots.getValue()) {
                removeCommandSession(s.getConnectionId());
            }
        }
        return modules.sppManager.disconnectByDevice(deviceId);
    }

    @Override
    public YoResult<Void> disconnectAllSpp() {
        YoBluetoothLogger.i(TAG, "断开全部 SPP 连接");
        clearCommandSessions();
        return modules.sppManager.disconnectAll();
    }

    @Override
    public YoResult<Void> send(@NonNull YoCbtSppConnectionId connectionId, @NonNull byte[] data) {
        YoBluetoothLogger.d(TAG, "发送 SPP 数据，连接 ID=" + connectionId
                + "，长度=" + data.length);
        return modules.sppManager.send(connectionId, data);
    }

    @Override
    public YoResult<Void> sendCommand(@NonNull YoCbtSppConnectionId connectionId,
                                      @NonNull YoCbtCommand command) {
        YoCbtCommandSession session = commandSession(connectionId);
        if (session == null) {
            return YoResult.failure(failure(YoCbtErrorCode.YO_CBT_SPP_CONNECTION_ID_NOT_FOUND,
                    "command session not found", null));
        }
        return session.submit(YoCbtCommandRequest.builder(nextOperationId("cmd"), command).build());
    }

    @Override
    public CbtInternalOperationHandle observeCommandResult(@NonNull YoCbtSppConnectionId connectionId,
                                                      @NonNull YoCbtCommandObserver observer) {
        YoCbtCommandSession session = commandSession(connectionId);
        if (session != null) session.observe(observer);
        return new CbtInternalOperationHandle(nextOperationId("cmd-obs"), () -> {
            YoCbtCommandSession s = commandSession(connectionId);
            if (s != null) s.removeObserver(observer);
        });
    }

    @Override
    public CbtInternalOperationHandle observeSppState(@NonNull YoCbtSppConnectionId connectionId,
                                                 @NonNull YoCbtSppStateObserver observer) {
        return new CbtInternalOperationHandle(nextOperationId("spp-state"),
                modules.sppManager.observeState(connectionId, observer));
    }

    @Override
    public YoResult<List<YoCbtSppConnectionSnapshot>> getActiveSppConnections() {
        return YoResult.success(modules.sppManager.snapshots());
    }

    @Override
    public YoResult<List<YoCbtSppConnectionSnapshot>> getSppConnections(@NonNull YoCbtDeviceId deviceId) {
        return YoResult.success(modules.sppManager.snapshots(deviceId));
    }

    @Override
    public YoResult<List<YoCbtDeviceSnapshot>> getDeviceSnapshots() {
        return YoResult.success(modules.snapshotStore.snapshot());
    }

    @Override
    public boolean isAdapterEnabled() {
        return modules.discoveryPlatform.isEnabled();
    }

    // ── Lifecycle ────────────────────────────────────────────────

    @Override
    public void release() {
        if (released) return;
        released = true;
        YoBluetoothLogger.i(TAG, "正在释放 YoCbtAndroidClient");
        modules.releaseAll();
        YoBluetoothLogger.i(TAG, "YoCbtAndroidClient 释放完成");
    }

    // ── Profile observer wiring ──────────────────────────────────

    @NonNull
    private List<YoCbtProfileStateObserver> collectProfileObservers(@NonNull Intent intent) {
        BluetoothDevice device = YoCbtIntentCompat.device(intent, BluetoothDevice.EXTRA_DEVICE);
        YoCbtDeviceIdentity identity = modules.mapper.map(device, null);
        if (identity == null) return new ArrayList<>(globalProfileObservers);
        synchronized (profileObservers) {
            CopyOnWriteArrayList<YoCbtProfileStateObserver> observers =
                    profileObservers.get(identity.getDeviceId());
            List<YoCbtProfileStateObserver> combined = new ArrayList<>(globalProfileObservers);
            if (observers != null) combined.addAll(observers);
            return combined;
        }
    }

    // ── Command session helpers ──────────────────────────────────

    @Nullable
    private YoCbtCommandSession commandSession(YoCbtSppConnectionId connectionId) {
        return modules.commandSessionRegistry.get(connectionId);
    }

    private void removeCommandSession(YoCbtSppConnectionId connectionId) {
        modules.commandSessionRegistry.remove(connectionId);
    }

    private void clearCommandSessions() {
        modules.commandSessionRegistry.clear();
    }

    @NonNull
    private List<YoCbtDeviceSnapshot> mergeConnectedSppDevices(
            @NonNull List<YoCbtDeviceSnapshot> profileSnapshots) {
        Map<YoCbtDeviceId, YoCbtDeviceSnapshot> merged = new LinkedHashMap<>();
        for (YoCbtDeviceSnapshot snapshot : profileSnapshots) {
            merged.put(snapshot.getDeviceId(), snapshot);
        }
        for (YoCbtSppConnectionSnapshot spp : modules.sppManager.snapshots()) {
            if (spp.getState() != com.yovo.lib_bluetooth.internal.classic.spp.connection.YoCbtSppConnectionState.CONNECTED) {
                continue;
            }
            YoCbtDeviceSnapshot snapshot = modules.snapshotStore.get(spp.getDeviceId());
            if (snapshot != null) merged.put(spp.getDeviceId(), snapshot);
        }
        return new ArrayList<>(merged.values());
    }

    private int countConnectedSppDevices() {
        java.util.HashSet<YoCbtDeviceId> devices = new java.util.HashSet<>();
        for (YoCbtSppConnectionSnapshot snapshot : modules.sppManager.snapshots()) {
            if (snapshot.getState()
                    == com.yovo.lib_bluetooth.internal.classic.spp.connection.YoCbtSppConnectionState.CONNECTED) {
                devices.add(snapshot.getDeviceId());
            }
        }
        return devices.size();
    }

    private void syncSppProfileState(@NonNull YoCbtDeviceId deviceId) {
        com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtProfileConnectionState aggregate =
                com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtProfileConnectionState.DISCONNECTED;
        boolean aclDisconnected = modules.aclLinkRepository.getAclState(deviceId)
                == YoCbtAclLinkState.DISCONNECTED;
        for (YoCbtSppConnectionSnapshot snapshot : aclDisconnected
                ? Collections.<YoCbtSppConnectionSnapshot>emptyList()
                : modules.sppManager.snapshots(deviceId)) {
            com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtProfileConnectionState candidate =
                    mapSppProfileState(snapshot.getState());
            if (candidate == com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtProfileConnectionState.CONNECTED) {
                aggregate = candidate;
                break;
            }
            if (candidate == com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtProfileConnectionState.CONNECTING) {
                aggregate = candidate;
            } else if (aggregate
                    == com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtProfileConnectionState.DISCONNECTED
                    && candidate
                    == com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtProfileConnectionState.DISCONNECTING) {
                aggregate = candidate;
            }
        }
        YoCbtDeviceProfileState state = new YoCbtDeviceProfileState(
                deviceId,
                YoCbtProfileType.SPP,
                aggregate,
                com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtProfileStateSource.SPP_CONNECTION_REGISTRY,
                System.currentTimeMillis());
        if (modules.profileStateRepository.applyProfileState(state) == null) return;
        notifyProfileStateChanged(state);
    }

    private void notifyProfileStateChanged(@NonNull YoCbtDeviceProfileState state) {
        modules.runtime.eventHub().publish(YoCbtEvents.PROFILE_STATE_CHANGED, state);
        List<YoCbtProfileStateObserver> observers;
        synchronized (profileObservers) {
            List<YoCbtProfileStateObserver> registered =
                    profileObservers.get(state.getDeviceId());
            observers = new ArrayList<>(globalProfileObservers);
            if (registered != null) observers.addAll(registered);
        }
        for (YoCbtProfileStateObserver profileObserver : observers) {
            modules.runtime.callback(() -> profileObserver.onProfileStateChanged(state));
        }
    }

    @NonNull
    private static com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtProfileConnectionState
    mapSppProfileState(
            @NonNull com.yovo.lib_bluetooth.internal.classic.spp.connection.YoCbtSppConnectionState state) {
        switch (state) {
            case CONNECTED:
                return com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtProfileConnectionState.CONNECTED;
            case PREPARING:
            case RESOLVING_SERVICE:
            case CREATING_SOCKET:
            case CONNECTING_SOCKET:
            case OPENING_STREAM:
            case STARTING_READER:
            case STARTING_WRITER:
                return com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtProfileConnectionState.CONNECTING;
            case DISCONNECTING:
                return com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtProfileConnectionState.DISCONNECTING;
            default:
                return com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtProfileConnectionState.DISCONNECTED;
        }
    }

    // ── Helpers ──────────────────────────────────────────────────

    private String nextOperationId(String prefix) {
        return prefix + "-" + System.currentTimeMillis() + "-" + operationSeq.incrementAndGet();
    }

    @NonNull
    private List<YoCbtSppEndpoint> resolveSdpEndpoints(@NonNull YoCbtDeviceId deviceId) {
        YoCbtDeviceSnapshot snapshot = modules.snapshotStore.get(deviceId);
        if (snapshot != null && !snapshot.getSdpSppEndpoints().isEmpty()) {
            return snapshot.getSdpSppEndpoints();
        }
        YoCbtSdpResult cached = modules.serviceCache.getResult(deviceId);
        if (cached != null) {
            return modules.sppEndpointResolver.resolve(cached);
        }
        return Collections.emptyList();
    }

    /**
     * 预解析本次连接将占用的物理 RFCOMM 通道号，用于跨端点冲突检测。
     * 通道端点直接返回其通道；UUID 端点尝试从缓存的 SDP 记录中查出对应通道；无法确定时返回 -1
     * （此时由连接成功后的 mPort 反射兜底，供后续连接做冲突判断）。
     */
    private int resolveRequestedChannel(@NonNull YoCbtDeviceId deviceId,
                                        @NonNull YoCbtSppEndpoint endpoint) {
        if (endpoint.isChannel()) {
            return endpoint.getChannel();
        }
        if (!endpoint.isUuid()) {
            return -1;
        }
        UUID uuid = endpoint.getUuid();
        if (uuid == null) {
            return -1;
        }
        YoCbtSdpResult cached = modules.serviceCache.getResult(deviceId);
        if (cached != null) {
            for (YoCbtServiceRecord record : cached.getServiceRecords()) {
                if (uuid.equals(record.getUuid()) && record.hasChannel()) {
                    return record.getChannel();
                }
            }
        }
        Integer learned = learnedSppChannels.get(learnedChannelKey(deviceId, uuid));
        return learned != null ? learned : -1;
    }

    /** 连接成功后，将 UUID 端点反推到的物理 RFCOMM 通道写入学习缓存。 */
    private void learnSppChannel(@NonNull YoCbtDeviceId deviceId,
                                 @NonNull YoCbtSppConnectionSnapshot snapshot) {
        YoCbtSppEndpoint endpoint = snapshot.getEndpoint();
        int channel = snapshot.getResolvedChannel();
        if (endpoint != null && endpoint.isUuid() && endpoint.getUuid() != null && channel > 0) {
            learnedSppChannels.put(learnedChannelKey(deviceId, endpoint.getUuid()), channel);
        }
    }

    @NonNull
    private static String learnedChannelKey(@NonNull YoCbtDeviceId deviceId, @NonNull UUID uuid) {
        return deviceId.value() + "|" + uuid;
    }

    private static YoCbtFailure failure(YoCbtErrorCode code, String message,
                                         @Nullable Throwable cause) {
        return new YoCbtFailure(code, message, cause, true, false, "api",
                null, null, null, null, "");
    }
}
