package com.yovo.lib_bluetooth.internal.classic.spp.connection;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.common.concurrency.YoTimeoutHandle;
import com.yovo.lib_bluetooth.internal.common.diagnostics.YoBluetoothLogger;
import com.yovo.lib_bluetooth.internal.common.result.YoResult;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceIdentity;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtErrorCode;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtFailure;
import com.yovo.lib_bluetooth.internal.classic.internal.android.spp.YoCbtSppRemoteDeviceGateway;
import com.yovo.lib_bluetooth.internal.classic.runtime.YoCbtEvents;
import com.yovo.lib_bluetooth.internal.classic.runtime.YoCbtRuntime;
import com.yovo.lib_bluetooth.internal.classic.runtime.YoCbtRuntimeScope;
import com.yovo.lib_bluetooth.internal.classic.spp.endpoint.YoCbtSppEndpoint;
import com.yovo.lib_bluetooth.internal.classic.spp.io.YoCbtSppReader;
import com.yovo.lib_bluetooth.internal.classic.spp.io.YoCbtSppWriter;
import com.yovo.lib_bluetooth.internal.classic.spp.policy.YoCbtSppConnectionAttemptPlanner;
import com.yovo.lib_bluetooth.internal.classic.spp.transport.YoCbtSppSocket;
import com.yovo.lib_bluetooth.internal.classic.spp.transport.YoCbtSppSocketFactory;
import com.yovo.lib_bluetooth.internal.classic.spp.transport.YoCbtSppTransport;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 单条 SPP 连接的建立、传输读写与生命周期管理。
 */

public final class YoCbtSppConnection {

    private static final String TAG = "YoCbtSpp";

    public interface Host {
        void onTerminal(@NonNull YoCbtSppConnectionId connectionId);

        @NonNull
        List<YoCbtSppStateObserver> stateObservers(@NonNull YoCbtSppConnectionId connectionId);
    }

    private final YoCbtSppConnectionId connectionId;
    private final YoCbtSppConnectionKey connectionKey;
    private final YoCbtDeviceId deviceId;
    private final YoCbtSppEndpoint endpoint;
    private final YoCbtSppConfig config;
    private final YoCbtSppConnectionObserver observer;
    private final YoCbtSppConnectionLifecycle lifecycle;
    private final YoCbtRuntime runtime;
    private final YoCbtSppRemoteDeviceGateway remoteDeviceGateway;
    private final YoCbtSppSocketFactory socketFactory;
    private final Host host;
    private final YoCbtRuntimeScope scope;

    private volatile YoCbtSppSocket socket;
    private volatile YoCbtSppTransport transport;
    private volatile YoCbtSppReader reader;
    private volatile YoCbtSppWriter writer;
    private volatile long updatedAtMillis = System.currentTimeMillis();
    private volatile YoCbtFailure pendingFailure;
    private volatile int resolvedChannel;
    private final AtomicBoolean terminal = new AtomicBoolean(false);

    public YoCbtSppConnection(@NonNull YoCbtSppConnectionId connectionId,
                              @NonNull YoCbtSppConnectionKey connectionKey,
                              @NonNull YoCbtSppConfig config,
                              @NonNull YoCbtSppConnectionObserver observer,
                              @Nullable List<YoCbtSppEndpoint> sdpResolvedEndpoints,
                              @NonNull YoCbtRuntime runtime,
                              @NonNull YoCbtSppRemoteDeviceGateway remoteDeviceGateway,
                              @NonNull YoCbtSppSocketFactory socketFactory,
                              @NonNull Host host) {
        this(connectionId, connectionKey, config, observer, sdpResolvedEndpoints, runtime,
                remoteDeviceGateway, socketFactory, host, -1);
    }

    public YoCbtSppConnection(@NonNull YoCbtSppConnectionId connectionId,
                              @NonNull YoCbtSppConnectionKey connectionKey,
                              @NonNull YoCbtSppConfig config,
                              @NonNull YoCbtSppConnectionObserver observer,
                              @Nullable List<YoCbtSppEndpoint> sdpResolvedEndpoints,
                              @NonNull YoCbtRuntime runtime,
                              @NonNull YoCbtSppRemoteDeviceGateway remoteDeviceGateway,
                              @NonNull YoCbtSppSocketFactory socketFactory,
                              @NonNull Host host,
                              int initialResolvedChannel) {
        this.connectionId = connectionId;
        this.connectionKey = connectionKey;
        this.deviceId = connectionKey.getDeviceId();
        this.endpoint = connectionKey.getEndpoint();
        this.config = config;
        this.observer = observer;
        this.lifecycle = new YoCbtSppConnectionLifecycle(connectionId);
        this.runtime = runtime;
        this.remoteDeviceGateway = remoteDeviceGateway;
        this.socketFactory = socketFactory;
        this.host = host;
        this.scope = runtime.createScope();
        if (initialResolvedChannel > 0) {
            this.resolvedChannel = initialResolvedChannel;
        } else if (endpoint.isChannel()) {
            this.resolvedChannel = endpoint.getChannel();
        } else {
            this.resolvedChannel = -1;
        }
    }

    @NonNull
    public YoCbtSppConnectionId getConnectionId() {
        return connectionId;
    }

    @NonNull
    public YoCbtSppConnectionKey getConnectionKey() {
        return connectionKey;
    }

    /**
     * 该连接实际占用的物理 RFCOMM 通道号；未知时返回 -1。
     * 用于跨端点（UUID 与原始通道）冲突检测。
     */
    public int getResolvedChannel() {
        return resolvedChannel;
    }

    public void cancelConnect() {
        close(true);
    }

    public void open(@Nullable List<YoCbtSppEndpoint> sdpResolvedEndpoints) {
        if (scope.isReleased()) return;

        if (remoteDeviceGateway.remoteDevice(deviceId) == null) {
            fail(YoCbtErrorCode.YO_CBT_SPP_DEVICE_INVALID, "Invalid SPP target device", null);
            return;
        }

        transition(YoCbtSppConnectionState.PREPARING, null);

        try {
            remoteDeviceGateway.cancelDiscovery();
        } catch (SecurityException e) {
            YoCbtErrorCode code = YoCbtSppPermissionFailures.fromThrowable(e);
            if (code == null) {
                code = YoCbtErrorCode.YO_CBT_PERMISSION_SCAN_DENIED;
            }
            fail(code, connectFailureMessage(code, endpoint, e), e);
            return;
        }
        YoCbtDeviceIdentity identity = remoteDeviceGateway.resolveIdentity(deviceId);
        if (identity != null) {
            runtime.callback(() -> observer.onSppDeviceResolved(identity));
        }

        int attempts = Math.max(1, config.getMaxConnectAttempts());
        List<YoCbtSppEndpoint> endpointAttempts =
                YoCbtSppConnectionAttemptPlanner.plan(config, endpoint, sdpResolvedEndpoints);
        Throwable lastError = null;
        for (YoCbtSppEndpoint attemptEndpoint : endpointAttempts) {
            if (terminal.get() || scope.isReleased()) return;
            for (int i = 0; i < attempts && !terminal.get() && !scope.isReleased(); i++) {
                try {
                    transition(YoCbtSppConnectionState.CREATING_SOCKET, null);
                    socket = socketFactory.create(deviceId.value(), attemptEndpoint, config);
                    if (terminal.get() || scope.isReleased()) {
                        closeQuietly(socket);
                        return;
                    }

                    transition(YoCbtSppConnectionState.CONNECTING_SOCKET, null);
                    YoTimeoutHandle timeoutHandle = runtime.scheduler().schedule(() -> {
                                    closeQuietly(socket);
                                    fail(YoCbtErrorCode.YO_CBT_SPP_CONNECT_TIMEOUT, "SPP connect timeout", null);
                                },
                            config.getConnectTimeoutMillis());
                    try {
                        socket.connect();
                    } finally {
                        timeoutHandle.cancel();
                    }
                    if (terminal.get() || scope.isReleased()) {
                        closeQuietly(socket);
                        return;
                    }

                    if (resolvedChannel <= 0) {
                        int negotiated = socket.getResolvedRfcommChannel();
                        if (negotiated > 0) {
                            resolvedChannel = negotiated;
                        } else if (attemptEndpoint.isChannel()) {
                            resolvedChannel = attemptEndpoint.getChannel();
                        }
                    }

                    transition(YoCbtSppConnectionState.OPENING_STREAM, null);
                    InputStream inputStream = socket.getInputStream();
                    OutputStream outputStream = socket.getOutputStream();
                    transport = new YoCbtSppTransport(socket, inputStream, outputStream);

                    transition(YoCbtSppConnectionState.STARTING_READER, null);
                    reader = new YoCbtSppReader(connectionId, inputStream, runtime, new YoCbtSppReader.Listener() {
                        @Override
                        public void onDataReceived(YoCbtSppConnectionId id, byte[] data) {
                            runtime.callback(() -> observer.onSppDataReceived(id, data));
                        }

                        @Override
                        public void onReadError(YoCbtSppConnectionId id, YoCbtFailure failure) {
                            if (!terminal.get()) {
                                fail(mapFailureCode(failure, YoCbtErrorCode.YO_CBT_SPP_READ_FAILED),
                                        "SPP read failed: " + failure.getMessage(), failure.getCause());
                            }
                        }

                        @Override
                        public void onReadComplete(YoCbtSppConnectionId id) {
                            if (!terminal.get()) close(false);
                        }
                    });
                    reader.start();

                    transition(YoCbtSppConnectionState.STARTING_WRITER, null);
                    writer = new YoCbtSppWriter(connectionId, outputStream, runtime,
                            new YoCbtSppWriter.Listener() {
                                @Override
                                public void onWriteCompleted(YoCbtSppConnectionId id, byte[] data) {
                                    runtime.callback(() -> observer.onSppDataSent(id, data));
                                }

                                @Override
                                public void onWriteError(YoCbtSppConnectionId id, YoCbtFailure failure) {
                                    if (!terminal.get()) {
                                        fail(mapFailureCode(failure, YoCbtErrorCode.YO_CBT_SPP_WRITE_FAILED),
                                                "SPP write failed: " + failure.getMessage(), failure.getCause());
                                    }
                                }
                            }, config.getWriteQueueCapacity());
                    writer.start();

                    transition(YoCbtSppConnectionState.CONNECTED, null);
                    runtime.eventHub().publish(YoCbtEvents.SPP_CONNECTION, snapshot());
                    return;
                } catch (Throwable e) {
                    if (terminal.get()) return;
                    closeQuietly(socket);
                    lastError = e;
                    YoCbtErrorCode mapped = mapConnectFailure(e, attemptEndpoint);
                    if (mapped != YoCbtErrorCode.YO_CBT_SPP_CONNECT_FAILED) {
                        fail(mapped, connectFailureMessage(mapped, attemptEndpoint, e), e);
                        return;
                    }
                    if (i + 1 < attempts) {
                        try {
                            Thread.sleep(config.getRetryDelayMillis());
                        } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                }
            }
        }
        if (!terminal.get()) {
            YoCbtErrorCode code = YoCbtErrorCode.YO_CBT_SPP_CONNECT_FAILED;
            YoCbtSppEndpoint lastEndpoint = endpointAttempts.isEmpty()
                    ? endpoint : endpointAttempts.get(endpointAttempts.size() - 1);
            if (lastError != null) {
                YoCbtErrorCode mapped = mapConnectFailure(lastError, lastEndpoint);
                if (mapped != YoCbtErrorCode.YO_CBT_SPP_CONNECT_FAILED) {
                    code = mapped;
                }
            }
            fail(code, connectFailureMessage(code, lastEndpoint, lastError), lastError);
        }
    }

    @NonNull
    public YoResult<Void> write(byte[] data) {
        if (data == null || data.length == 0) {
            return YoResult.failure(failure(YoCbtErrorCode.YO_CBT_SPP_WRITE_FAILED,
                    "SPP write data is null or empty", null));
        }
        if (!lifecycle.isActive()) {
            return YoResult.failure(failure(YoCbtErrorCode.YO_CBT_SPP_NOT_CONNECTED,
                    "SPP connection is not active: " + lifecycle.getCurrentPhase(), null));
        }
        YoCbtSppWriter target = writer;
        if (target == null || !target.isRunning()) {
            return YoResult.failure(failure(YoCbtErrorCode.YO_CBT_SPP_WRITE_FAILED,
                    "SPP writer is not running", null));
        }
        return target.write(data);
    }

    public void close(boolean local) {
        if (!terminal.compareAndSet(false, true)) return;

        transition(YoCbtSppConnectionState.DISCONNECTING, null);
        stopIo();
        closeTransport();

        pendingFailure = failure(local
                        ? YoCbtErrorCode.YO_CBT_SPP_LOCAL_CLOSED
                        : YoCbtErrorCode.YO_CBT_SPP_REMOTE_CLOSED,
                local ? "SPP connection closed locally" : "SPP connection closed by remote peer",
                null);

        transition(YoCbtSppConnectionState.DISCONNECTED, pendingFailure);
        host.onTerminal(connectionId);
        scope.release();
    }

    public void transition(@NonNull YoCbtSppConnectionState next, @Nullable YoCbtFailure failure) {
        if (failure != null) {
            pendingFailure = failure;
        }
        lifecycle.enter(next, pendingFailure);
        updatedAtMillis = System.currentTimeMillis();
        YoCbtSppConnectionSnapshot snap = snapshot();
        runtime.callback(() -> observer.onSppStateChanged(snap));
        for (YoCbtSppStateObserver stateObserver : host.stateObservers(connectionId)) {
            runtime.callback(() -> stateObserver.onSppStateChanged(snap));
        }
    }

    @NonNull
    public YoCbtSppConnectionSnapshot snapshot() {
        return new YoCbtSppConnectionSnapshot(
                connectionId,
                deviceId,
                endpoint,
                lifecycle.getCurrentPhase(),
                updatedAtMillis,
                pendingFailure != null ? pendingFailure : lifecycle.getFailure(),
                resolvedChannel);
    }

    private void fail(YoCbtErrorCode code, String message, Throwable throwable) {
        if (!terminal.compareAndSet(false, true)) return;
        YoBluetoothLogger.e(TAG, message + "，连接ID=" + connectionId, throwable);

        pendingFailure = failure(code, message, throwable);
        transition(YoCbtSppConnectionState.FAILED, pendingFailure);

        stopIo();
        closeTransport();
        host.onTerminal(connectionId);
        scope.release();
    }

    private void stopIo() {
        YoCbtSppReader r = reader;
        if (r != null) r.stop();
        reader = null;

        YoCbtSppWriter w = writer;
        if (w != null) w.stop();
        writer = null;
    }

    private void closeTransport() {
        YoCbtSppTransport t = transport;
        if (t != null) {
            t.close();
        } else {
            closeQuietly(socket);
        }
        transport = null;
        socket = null;
    }

    @NonNull
    private YoCbtFailure failure(YoCbtErrorCode code, String message, Throwable cause) {
        return new YoCbtFailure(code, message, cause, true, false, "spp",
                null, null, connectionId, endpoint, "");
    }

    @NonNull
    private static YoCbtErrorCode mapFailureCode(@NonNull YoCbtFailure failure,
                                                 @NonNull YoCbtErrorCode fallback) {
        return failure.getErrorCode() instanceof YoCbtErrorCode
                ? (YoCbtErrorCode) failure.getErrorCode()
                : fallback;
    }

    @NonNull
    private static YoCbtErrorCode mapConnectFailure(@NonNull Throwable throwable,
                                                    @NonNull YoCbtSppEndpoint attemptEndpoint) {
        YoCbtErrorCode permission = YoCbtSppPermissionFailures.fromThrowable(throwable);
        if (permission != null) {
            return permission;
        }
        String className = throwable.getClass().getName();
        if (className.contains("YoCbtHiddenApiException")) {
            String text = String.valueOf(throwable.getMessage());
            if (text.contains("DISABLED_BY_POLICY")) {
                return YoCbtErrorCode.YO_CBT_SPP_CHANNEL_HIDDEN_API_BLOCKED;
            }
            if (text.contains("INVALID_ARGUMENT")) {
                return YoCbtErrorCode.YO_CBT_SPP_CHANNEL_INVALID;
            }
            if (text.contains("METHOD_NOT_AVAILABLE")) {
                return YoCbtErrorCode.YO_CBT_SPP_CHANNEL_HIDDEN_API_NOT_AVAILABLE;
            }
            if (text.contains("REFLECTION_FAILED")) {
                return YoCbtErrorCode.YO_CBT_SPP_CHANNEL_REFLECTION_FAILED;
            }
            if (text.contains("INVOCATION_FAILED")) {
                return YoCbtErrorCode.YO_CBT_SPP_CHANNEL_SOCKET_CREATE_FAILED;
            }
            return YoCbtErrorCode.YO_CBT_SPP_SOCKET_CREATE_FAILED;
        }
        if (attemptEndpoint.isChannel() && isLikelyChannelUnavailable(throwable)) {
            return YoCbtErrorCode.YO_CBT_SPP_CHANNEL_CONNECT_FAILED;
        }
        return YoCbtErrorCode.YO_CBT_SPP_CONNECT_FAILED;
    }

    private static boolean isLikelyChannelUnavailable(@NonNull Throwable throwable) {
        if (!(throwable instanceof IOException)) {
            return false;
        }
        String text = String.valueOf(throwable.getMessage()).toLowerCase();
        return text.contains("socket might closed")
                || text.contains("read failed")
                || text.contains("connection refused")
                || text.contains("service discovery")
                || text.contains("unable to connect");
    }

    @NonNull
    private static String connectFailureMessage(@NonNull YoCbtErrorCode code,
                                                @NonNull YoCbtSppEndpoint attemptEndpoint,
                                                @Nullable Throwable throwable) {
        if (attemptEndpoint.isChannel()) {
            int channel = attemptEndpoint.getChannel();
            switch (code) {
                case YO_CBT_PERMISSION_CONNECT_DENIED:
                    return "RFCOMM channel " + channel
                            + " connect requires Bluetooth connect permission";
                case YO_CBT_PERMISSION_SCAN_DENIED:
                    return "RFCOMM channel " + channel
                            + " connect requires Bluetooth scan permission";
                case YO_CBT_SPP_CHANNEL_INVALID:
                    return "RFCOMM channel " + channel + " is invalid";
                case YO_CBT_SPP_CHANNEL_CONNECT_FAILED:
                    return "RFCOMM channel " + channel + " is unavailable on this device";
                case YO_CBT_SPP_CHANNEL_HIDDEN_API_BLOCKED:
                    return "RFCOMM channel " + channel + " is blocked by hidden API policy";
                case YO_CBT_SPP_CHANNEL_HIDDEN_API_NOT_AVAILABLE:
                    return "RFCOMM channel " + channel + " hidden API is not available on this ROM";
                case YO_CBT_SPP_CHANNEL_REFLECTION_FAILED:
                case YO_CBT_SPP_CHANNEL_SOCKET_CREATE_FAILED:
                    return "Failed to create socket for RFCOMM channel " + channel;
                default:
                    break;
            }
        }
        if (code == YoCbtErrorCode.YO_CBT_PERMISSION_CONNECT_DENIED) {
            return "SPP connect requires Bluetooth connect permission";
        }
        if (code == YoCbtErrorCode.YO_CBT_PERMISSION_SCAN_DENIED) {
            return "SPP connect requires Bluetooth scan permission";
        }
        if (throwable != null && throwable.getMessage() != null && !throwable.getMessage().isEmpty()) {
            return "SPP connect failed: " + throwable.getMessage();
        }
        return "SPP connect failed";
    }

    private static void closeQuietly(@Nullable YoCbtSppSocket closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (Throwable ignored) {
        }
    }
}
