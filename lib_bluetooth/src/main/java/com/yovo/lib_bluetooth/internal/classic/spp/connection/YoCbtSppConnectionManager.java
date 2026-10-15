package com.yovo.lib_bluetooth.internal.classic.spp.connection;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.common.result.YoResult;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtErrorCode;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtFailure;
import com.yovo.lib_bluetooth.internal.classic.internal.android.spp.YoCbtSppRemoteDeviceGateway;
import com.yovo.lib_bluetooth.internal.classic.protocol.command.YoCbtCommandSessionRegistry;
import com.yovo.lib_bluetooth.internal.classic.runtime.YoCbtRuntime;
import com.yovo.lib_bluetooth.internal.classic.spp.endpoint.YoCbtSppEndpoint;
import com.yovo.lib_bluetooth.internal.classic.spp.policy.YoCbtSppConnectionPolicyValidator;
import com.yovo.lib_bluetooth.internal.classic.spp.registry.YoCbtSppConnectionRegistry;
import com.yovo.lib_bluetooth.internal.classic.spp.registry.YoCbtSppConnectionSelector;
import com.yovo.lib_bluetooth.internal.classic.spp.transport.YoCbtSppSocketFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description SPP 连接管理器，负责创建、查找、释放多条 SPP 连接并通知观察者。
 */

public final class YoCbtSppConnectionManager implements YoCbtSppConnection.Host {

    private final YoCbtRuntime runtime;
    private final YoCbtSppRemoteDeviceGateway remoteDeviceGateway;
    private final YoCbtSppSocketFactory socketFactory;
    private final YoCbtCommandSessionRegistry commandSessionRegistry;
    private final AtomicLong sequence = new AtomicLong();
    private final Map<YoCbtSppConnectionId, YoCbtSppConnection> connections = new LinkedHashMap<>();
    private final YoCbtSppConnectionRegistry<YoCbtSppConnection> registry = new YoCbtSppConnectionRegistry<>();
    private final Map<YoCbtSppConnectionId, CopyOnWriteArrayList<YoCbtSppStateObserver>> stateObservers =
            new LinkedHashMap<>();

    public YoCbtSppConnectionManager(@NonNull YoCbtRuntime runtime,
                                     @NonNull YoCbtSppRemoteDeviceGateway remoteDeviceGateway,
                                     @NonNull YoCbtSppSocketFactory socketFactory,
                                     @NonNull YoCbtCommandSessionRegistry commandSessionRegistry) {
        this.runtime = runtime;
        this.remoteDeviceGateway = remoteDeviceGateway;
        this.socketFactory = socketFactory;
        this.commandSessionRegistry = commandSessionRegistry;
    }

    /**
     * @param requestedChannel 本次连接预解析到的物理 RFCOMM 通道号（通道端点即其通道，
     *                         UUID 端点经 SDP 解析得到），未知时传 -1。用于跨端点冲突检测：
     *                         若已有连接占用同一物理通道，则直接拒绝而不破坏已有连接。
     */
    @NonNull
    public YoResult<YoCbtSppConnectionId> connect(@NonNull YoCbtDeviceId deviceId,
                                                  @NonNull YoCbtSppEndpoint endpoint,
                                                  @Nullable YoCbtSppConfig config,
                                                  @Nullable YoCbtSppConnectionObserver observer,
                                                  @Nullable List<YoCbtSppEndpoint> sdpResolvedEndpoints,
                                                  int requestedChannel) {
        YoCbtSppConfig actualConfig = config == null ? YoCbtSppConfig.builder().build() : config;
        YoCbtSppConnectionObserver actualObserver =
                observer == null ? new YoCbtSppConnectionObserver() {} : observer;

        YoResult<Void> policyResult = YoCbtSppConnectionPolicyValidator.validate(
                actualConfig.getConnectionPolicy(), endpoint);
        if (policyResult.isFailure()) {
            return YoResult.failure(policyResult.getFailure());
        }

        YoCbtSppConnectionKey connectionKey = new YoCbtSppConnectionKey(deviceId, endpoint,
                endpoint.getSecurityMode(), YoCbtSppConnectionRole.CLIENT,
                actualConfig.getConnectionPolicy());
        synchronized (connections) {
            if (registry.hasActiveKey(connectionKey)) {
                return YoResult.failure(failure(YoCbtErrorCode.YO_CBT_SPP_CONNECTION_KEY_CONFLICT,
                        "An active SPP connection already exists for " + deviceId + " / " + endpoint,
                        null, endpoint));
            }
            if (requestedChannel > 0 && hasActivePhysicalChannel(deviceId, requestedChannel)) {
                return YoResult.failure(failure(YoCbtErrorCode.YO_CBT_SPP_CHANNEL_IN_USE,
                        "RFCOMM channel " + requestedChannel
                                + " is already in use by another connection on " + deviceId,
                        null, endpoint));
            }
        }

        YoCbtSppConnectionId connectionId = new YoCbtSppConnectionId(
                "spp-" + System.currentTimeMillis() + "-" + sequence.incrementAndGet());
        YoCbtSppConnection connection = new YoCbtSppConnection(
                connectionId, connectionKey, actualConfig, actualObserver, sdpResolvedEndpoints,
                runtime, remoteDeviceGateway, socketFactory, this, requestedChannel);
        synchronized (connections) {
            connections.put(connectionId, connection);
            registry.put(connectionId, connectionKey, connection);
        }
        connection.transition(YoCbtSppConnectionState.PREPARING, null);
        List<YoCbtSppEndpoint> endpoints = sdpResolvedEndpoints;
        runtime.io(() -> connection.open(endpoints));
        return YoResult.success(connectionId);
    }

    /** 调用方需持有 {@code connections} 锁。判断该设备是否已有连接占用指定物理 RFCOMM 通道。 */
    private boolean hasActivePhysicalChannel(@NonNull YoCbtDeviceId deviceId, int channel) {
        for (YoCbtSppConnectionId id : registry.idsByDevice(deviceId)) {
            YoCbtSppConnection connection = connections.get(id);
            if (connection != null && connection.getResolvedChannel() == channel) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    public YoResult<Void> cancelConnect(@NonNull YoCbtSppConnectionId connectionId) {
        YoCbtSppConnection connection = getConnection(connectionId);
        if (connection == null) {
            return YoResult.failure(failure(YoCbtErrorCode.YO_CBT_SPP_CONNECTION_ID_NOT_FOUND,
                    "SPP connection not found", connectionId, null));
        }
        connection.cancelConnect();
        return YoResult.success(null);
    }

    @NonNull
    public YoCbtSppConnectionSelector selector() {
        Map<YoCbtSppConnectionId, YoCbtSppConnectionSnapshot> map = new LinkedHashMap<>();
        synchronized (connections) {
            for (YoCbtSppConnection connection : registry.all()) {
                map.put(connection.getConnectionId(), connection.snapshot());
            }
        }
        return new YoCbtSppConnectionSelector(map);
    }

    @NonNull
    public YoResult<Void> send(@NonNull YoCbtSppConnectionId connectionId, @NonNull byte[] data) {
        YoCbtSppConnection connection = getConnection(connectionId);
        if (connection == null) {
            return YoResult.failure(failure(YoCbtErrorCode.YO_CBT_SPP_CONNECTION_ID_NOT_FOUND,
                    "SPP connection not found", connectionId, null));
        }
        return connection.write(data);
    }

    @NonNull
    public YoResult<Void> disconnect(@NonNull YoCbtSppConnectionId connectionId) {
        YoCbtSppConnection connection;
        synchronized (connections) {
            connection = connections.remove(connectionId);
            registry.remove(connectionId);
        }
        if (connection == null) {
            return YoResult.failure(failure(YoCbtErrorCode.YO_CBT_SPP_CONNECTION_ID_NOT_FOUND,
                    "SPP connection not found", connectionId, null));
        }
        connection.close(true);
        return YoResult.success(null);
    }

    @NonNull
    public YoResult<Void> disconnectByDevice(@NonNull YoCbtDeviceId deviceId) {
        List<YoCbtSppConnectionId> ids = new ArrayList<>();
        synchronized (connections) {
            ids.addAll(registry.idsByDevice(deviceId));
        }
        for (YoCbtSppConnectionId id : ids) disconnect(id);
        return YoResult.success(null);
    }

    /** Terminates SDK-owned sockets after the underlying ACL transport is lost. */
    public void onAclDisconnected(@NonNull YoCbtDeviceId deviceId) {
        List<YoCbtSppConnection> affected = new ArrayList<>();
        synchronized (connections) {
            for (YoCbtSppConnectionId id : registry.idsByDevice(deviceId)) {
                YoCbtSppConnection connection = connections.get(id);
                if (connection != null) affected.add(connection);
            }
        }
        for (YoCbtSppConnection connection : affected) {
            connection.close(false);
        }
    }

    @NonNull
    public YoResult<Void> disconnectAll() {
        List<YoCbtSppConnectionId> ids;
        synchronized (connections) {
            ids = new ArrayList<>(connections.keySet());
        }
        for (YoCbtSppConnectionId id : ids) disconnect(id);
        return YoResult.success(null);
    }

    @NonNull
    public List<YoCbtSppConnectionSnapshot> snapshots() {
        return selector().all();
    }

    @NonNull
    public List<YoCbtSppConnectionSnapshot> snapshots(@NonNull YoCbtDeviceId deviceId) {
        return selector().byDevice(deviceId);
    }

    @NonNull
    public Runnable observeState(@NonNull YoCbtSppConnectionId connectionId,
                                 @NonNull YoCbtSppStateObserver observer) {
        YoCbtSppConnectionSnapshot snapshot = null;
        synchronized (connections) {
            stateObservers.computeIfAbsent(connectionId, k -> new CopyOnWriteArrayList<>()).addIfAbsent(observer);
            YoCbtSppConnection connection = connections.get(connectionId);
            if (connection != null) snapshot = connection.snapshot();
        }
        if (snapshot != null) {
            YoCbtSppConnectionSnapshot current = snapshot;
            runtime.callback(() -> observer.onSppStateChanged(current));
        }
        return () -> {
            synchronized (connections) {
                List<YoCbtSppStateObserver> observers = stateObservers.get(connectionId);
                if (observers != null) observers.remove(observer);
            }
        };
    }

    public void release() {
        disconnectAll();
        synchronized (connections) {
            stateObservers.clear();
            registry.clear();
        }
    }

    @Override
    public void onTerminal(@NonNull YoCbtSppConnectionId connectionId) {
        commandSessionRegistry.remove(connectionId);
        synchronized (connections) {
            connections.remove(connectionId);
            registry.remove(connectionId);
            stateObservers.remove(connectionId);
        }
    }

    @NonNull
    @Override
    public List<YoCbtSppStateObserver> stateObservers(@NonNull YoCbtSppConnectionId connectionId) {
        synchronized (connections) {
            List<YoCbtSppStateObserver> observers = stateObservers.get(connectionId);
            return observers == null ? Collections.emptyList() : new ArrayList<>(observers);
        }
    }

    @Nullable
    private YoCbtSppConnection getConnection(YoCbtSppConnectionId id) {
        synchronized (connections) {
            return connections.get(id);
        }
    }

    private static YoCbtFailure failure(YoCbtErrorCode code, String message,
                                        YoCbtSppConnectionId id, YoCbtSppEndpoint endpoint) {
        return new YoCbtFailure(code, message, null, true, false, "spp",
                null, null, id, endpoint, "");
    }
}
