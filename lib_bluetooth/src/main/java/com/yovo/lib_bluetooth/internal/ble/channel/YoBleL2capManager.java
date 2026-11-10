package com.yovo.lib_bluetooth.internal.ble.channel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.ble.device.YoBleCapabilityInspector;
import com.yovo.lib_bluetooth.internal.ble.diagnostics.YoBleTimelineRecorder;
import com.yovo.lib_bluetooth.internal.ble.error.YoBleErrorMapper;
import com.yovo.lib_bluetooth.internal.ble.runtime.YoBleRuntime;
import com.yovo.lib_bluetooth.internal.common.lifecycle.YoReleasable;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleErrorCode;
import com.yovo.lib_bluetooth.model.ble.BleFailure;
import com.yovo.lib_bluetooth.model.ble.BleL2capChannelHandle;
import com.yovo.lib_bluetooth.model.ble.BleL2capChannelObserver;
import com.yovo.lib_bluetooth.model.ble.BleL2capServerObserver;
import com.yovo.lib_bluetooth.model.ble.BleOperationHandle;
import com.yovo.lib_bluetooth.model.ble.BleValueObserver;
import com.yovo.lib_bluetooth.model.common.Result;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * BLE L2CAP CoC 面向连接高速字节流通道管理器：
 * <ul>
 *   <li>API &lt; 29 安全降级返回 {@link BleErrorCode#UNSUPPORTED_OPERATION}；</li>
 *   <li>前置 CONNECT 权限、合法 MAC 与蓝牙状态校验；</li>
 *   <li>在 {@code ioExecutor} 执行阻塞读循环与同步写入，幂等关闭并全量托管于 {@link #release()}。</li>
 * </ul>
 */
public final class YoBleL2capManager implements YoReleasable {

    private static final String MODULE = "ble-l2cap";
    private static final int READ_BUFFER_SIZE = 4096;

    private final YoBleRuntime runtime;
    private final YoBleCapabilityInspector capabilityInspector;
    private final YoBleL2capPlatform platform;
    private final YoBleTimelineRecorder timeline;
    private final CopyOnWriteArrayList<ChannelHandleImpl> activeChannels = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Closeable> pendingOpenHooks = new CopyOnWriteArrayList<>();
    private final AtomicLong seq = new AtomicLong();
    private volatile boolean released = false;

    public YoBleL2capManager(@NonNull YoBleRuntime runtime,
                              @NonNull YoBleCapabilityInspector capabilityInspector,
                              @NonNull YoBleL2capPlatform platform,
                              @NonNull YoBleTimelineRecorder timeline) {
        this.runtime = runtime;
        this.capabilityInspector = capabilityInspector;
        this.platform = platform;
        this.timeline = timeline;
    }

    @NonNull
    public BleOperationHandle openChannel(@NonNull BleDeviceId deviceId,
                                          int psm,
                                          boolean secure,
                                          @NonNull BleL2capChannelObserver observer) {
        String opId = "ble-l2cap-" + seq.incrementAndGet();
        if (released || runtime.isReleased()) {
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
        if (psm <= 0) {
            BleFailure f = YoBleErrorMapper.failure(
                    BleErrorCode.ILLEGAL_ARGUMENT, MODULE, "PSM must be positive: " + psm, deviceId);
            runtime.callback(() -> observer.onFailed(f));
            return BleOperationHandle.completed(opId);
        }
        if (capabilityInspector.sdkInt() < 29) {
            BleFailure f = YoBleErrorMapper.failure(
                    BleErrorCode.UNSUPPORTED_OPERATION, MODULE,
                    "L2CAP CoC channel requires Android 10 (API 29+)", deviceId);
            runtime.callback(() -> observer.onFailed(f));
            return BleOperationHandle.completed(opId);
        }
        if (!capabilityInspector.hasConnectPermission()) {
            BleFailure f = YoBleErrorMapper.failure(
                    BleErrorCode.PERMISSION_CONNECT_DENIED, MODULE,
                    "Missing BLUETOOTH_CONNECT permission for L2CAP channel", deviceId);
            runtime.callback(() -> observer.onFailed(f));
            return BleOperationHandle.completed(opId);
        }
        if (!capabilityInspector.isAdapterEnabled()) {
            BleFailure f = YoBleErrorMapper.failure(
                    BleErrorCode.BLUETOOTH_DISABLED, MODULE,
                    "Bluetooth adapter disabled", deviceId);
            runtime.callback(() -> observer.onFailed(f));
            return BleOperationHandle.completed(opId);
        }

        AtomicBoolean cancelled = new AtomicBoolean(false);
        AtomicBoolean terminalNotified = new AtomicBoolean(false);
        AtomicReference<Closeable> cancelHookRef = new AtomicReference<>();
        AtomicReference<ChannelHandleImpl> handleSlot = new AtomicReference<>();

        runtime.io(() -> {
            if (cancelled.get() || released) {
                if (cancelled.get() && terminalNotified.compareAndSet(false, true)) {
                    BleFailure cancelErr = YoBleErrorMapper.failure(
                            BleErrorCode.OPERATION_CANCELLED, MODULE,
                            "L2CAP open cancelled", deviceId);
                    runtime.callback(() -> observer.onFailed(cancelErr));
                }
                return;
            }
            YoBleL2capPlatform.SocketConnection socket;
            try {
                socket = platform.openClientChannel(deviceId, psm, secure, hook -> {
                    if (hook != null) {
                        cancelHookRef.set(hook);
                        pendingOpenHooks.add(hook);
                        if (cancelled.get() || released) {
                            if (cancelHookRef.compareAndSet(hook, null)) {
                                pendingOpenHooks.remove(hook);
                                closeQuietly(hook);
                            }
                        }
                    }
                });
            } catch (Throwable t) {
                Closeable leftover = cancelHookRef.getAndSet(null);
                if (leftover != null) {
                    pendingOpenHooks.remove(leftover);
                }
                if (!cancelled.get() && !released && terminalNotified.compareAndSet(false, true)) {
                    BleFailure f = YoBleErrorMapper.failure(
                            BleErrorCode.L2CAP_OPEN_FAILED, MODULE,
                            "Failed to open L2CAP channel psm=" + psm + ": " + t.getMessage(),
                            t, BleFailure.NO_RAW_STATUS, deviceId, null, null);
                    timeline.record(MODULE, "openChannel failed psm=" + psm + " err=" + t.getMessage());
                    runtime.callback(() -> observer.onFailed(f));
                }
                return;
            }

            Closeable openedHook = cancelHookRef.getAndSet(null);
            if (openedHook != null) {
                pendingOpenHooks.remove(openedHook);
            }

            if (cancelled.get() || released || !terminalNotified.compareAndSet(false, true)) {
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
                if (cancelled.get() && terminalNotified.compareAndSet(false, true)) {
                    BleFailure cancelErr = YoBleErrorMapper.failure(
                            BleErrorCode.OPERATION_CANCELLED, MODULE,
                            "L2CAP open cancelled", deviceId);
                    runtime.callback(() -> observer.onFailed(cancelErr));
                }
                return;
            }

            ChannelHandleImpl handle = new ChannelHandleImpl(opId, deviceId, psm, socket);
            handleSlot.set(handle);
            activeChannels.add(handle);
            timeline.record(MODULE, "L2CAP channel opened device=" + deviceId + " psm=" + psm + " secure=" + secure);
            runtime.callback(() -> observer.onChannelOpened(handle));
            Thread reader = new Thread(() -> readLoop(handle, socket, observer),
                    "yo-ble-l2cap-" + deviceId.value().replace(':', '-'));
            reader.setDaemon(true);
            reader.start();
        });

        return new BleOperationHandle(opId, () -> {
            if (cancelled.compareAndSet(false, true)) {
                ChannelHandleImpl h = handleSlot.get();
                if (h != null) {
                    h.close();
                } else {
                    Closeable hook = cancelHookRef.getAndSet(null);
                    if (hook != null) {
                        pendingOpenHooks.remove(hook);
                        closeQuietly(hook);
                    }
                    if (terminalNotified.compareAndSet(false, true)) {
                        BleFailure cancelErr = YoBleErrorMapper.failure(
                                BleErrorCode.OPERATION_CANCELLED, MODULE,
                                "L2CAP open cancelled", deviceId);
                        runtime.callback(() -> observer.onFailed(cancelErr));
                    }
                }
            }
        });
    }

    @NonNull
    public BleOperationHandle openServer(boolean secure, @NonNull BleL2capServerObserver observer) {
        String opId = "ble-l2cap-srv-" + seq.incrementAndGet();
        BleFailure rejected = serverPreflight();
        if (rejected != null) {
            runtime.callback(() -> observer.onFailed(rejected));
            return BleOperationHandle.completed(opId);
        }
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AtomicReference<YoBleL2capPlatform.ListeningSocket> serverRef = new AtomicReference<>();
        Thread acceptThread = new Thread(() -> acceptLoop(secure, observer, cancelled, serverRef), "yo-ble-l2cap-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
        return new BleOperationHandle(opId, () -> {
            cancelled.set(true);
            YoBleL2capPlatform.ListeningSocket server = serverRef.get();
            if (server != null) {
                try {
                    server.close();
                } catch (IOException ignored) {
                }
            }
        });
    }

    public void onAdapterTurnedOff() {
        closeAllChannels();
    }

    @Override
    public void release() {
        released = true;
        closeAllChannels();
    }

    private void closeAllChannels() {
        List<Closeable> hookCopy = new ArrayList<>(pendingOpenHooks);
        pendingOpenHooks.clear();
        for (Closeable hook : hookCopy) {
            closeQuietly(hook);
        }
        List<ChannelHandleImpl> copy = new ArrayList<>(activeChannels);
        activeChannels.clear();
        for (ChannelHandleImpl h : copy) {
            h.close();
        }
    }

    private void readLoop(@NonNull ChannelHandleImpl handle,
                          @NonNull YoBleL2capPlatform.SocketConnection socket,
                          @NonNull BleL2capChannelObserver observer) {
        byte[] buffer = new byte[READ_BUFFER_SIZE];
        while (handle.isOpen() && !released) {
            try {
                int n = socket.read(buffer);
                if (n < 0) {
                    handle.closeInternal(true, observer);
                    return;
                }
                if (n > 0) {
                    byte[] chunk = Arrays.copyOf(buffer, n);
                    runtime.callback(() -> observer.onDataReceived(chunk));
                }
            } catch (Throwable e) {
                if (!handle.closedByLocal.get() && handle.isOpen()) {
                    BleFailure ioErr = YoBleErrorMapper.failure(
                            BleErrorCode.L2CAP_IO_FAILED, MODULE,
                            "L2CAP read error: " + e.getMessage(),
                            e, BleFailure.NO_RAW_STATUS, handle.deviceId, null, null);
                    runtime.callback(() -> observer.onFailed(ioErr));
                    handle.closeInternal(true, observer);
                }
                return;
            }
        }
    }

    @Nullable
    private BleFailure serverPreflight() {
        if (released || runtime.isReleased()) {
            return YoBleErrorMapper.failure(BleErrorCode.RELEASED, MODULE, "Client released");
        }
        if (capabilityInspector.sdkInt() < 29) {
            return YoBleErrorMapper.failure(
                    BleErrorCode.UNSUPPORTED_OPERATION, MODULE, "L2CAP CoC channel requires Android 10 (API 29+)");
        }
        if (!capabilityInspector.hasConnectPermission()) {
            return YoBleErrorMapper.failure(
                    BleErrorCode.PERMISSION_CONNECT_DENIED, MODULE, "Missing BLUETOOTH_CONNECT permission for L2CAP channel");
        }
        if (!capabilityInspector.isAdapterEnabled()) {
            return YoBleErrorMapper.failure(
                    BleErrorCode.BLUETOOTH_DISABLED, MODULE, "Bluetooth adapter disabled");
        }
        return null;
    }

    private void acceptLoop(boolean secure,
                            @NonNull BleL2capServerObserver observer,
                            @NonNull AtomicBoolean cancelled,
                            @NonNull AtomicReference<YoBleL2capPlatform.ListeningSocket> serverRef) {
        YoBleL2capPlatform.ListeningSocket server = null;
        AtomicBoolean listening = new AtomicBoolean(false);
        try {
            server = platform.openServer(secure, hook -> {
                if (hook == null) {
                    return;
                }
                pendingOpenHooks.add(hook);
                if (cancelled.get() || released) {
                    pendingOpenHooks.remove(hook);
                    closeQuietly(hook);
                }
            });
            serverRef.set(server);
            int psm = server.psm();
            listening.set(true);
            runtime.callback(() -> observer.onListening(psm));
            while (!cancelled.get() && !released) {
                YoBleL2capPlatform.SocketConnection socket = server.accept();
                if (cancelled.get() || released) {
                    try {
                        socket.close();
                    } catch (IOException ignored) {
                    }
                    break;
                }
                BleDeviceId remote = socket.remoteDeviceId();
                ChannelHandleImpl handle = new ChannelHandleImpl(
                        "ble-l2cap-peer-" + seq.incrementAndGet(), remote, psm, socket);
                activeChannels.add(handle);
                BleL2capChannelObserver inbound = new BleL2capChannelObserver() {
                    @Override
                    public void onChannelOpened(@NonNull BleL2capChannelHandle channel) {
                    }

                    @Override
                    public void onDataReceived(@NonNull byte[] chunk) {
                        observer.onDataReceived(remote, chunk);
                    }

                    @Override
                    public void onChannelClosed() {
                        observer.onClientClosed(remote);
                    }

                    @Override
                    public void onFailed(@NonNull BleFailure failure) {
                        observer.onFailed(failure);
                    }
                };
                runtime.callback(() -> observer.onClientAccepted(remote, handle));
                Thread reader = new Thread(() -> readLoop(handle, socket, inbound),
                        "yo-ble-l2cap-" + remote.value().replace(':', '-'));
                reader.setDaemon(true);
                reader.start();
            }
            if (listening.get()) {
                runtime.callback(observer::onServerClosed);
            }
        } catch (Throwable thrown) {
            if (cancelled.get() || released) {
                if (listening.get()) {
                    runtime.callback(observer::onServerClosed);
                } else {
                    BleFailure cancelledFailure = YoBleErrorMapper.failure(
                            BleErrorCode.OPERATION_CANCELLED, MODULE, "L2CAP server cancelled");
                    runtime.callback(() -> observer.onFailed(cancelledFailure));
                }
                return;
            }
            BleFailure failure = YoBleErrorMapper.failure(
                    BleErrorCode.L2CAP_OPEN_FAILED, MODULE,
                    "L2CAP server failed: " + thrown.getMessage(),
                    thrown, BleFailure.NO_RAW_STATUS, null, null, null);
            runtime.callback(() -> observer.onFailed(failure));
        } finally {
            if (server != null) {
                try {
                    server.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static void closeQuietly(@NonNull Closeable c) {
        try {
            c.close();
        } catch (Throwable ignored) {
        }
    }

    private static final class WriteJob {
        private static final WriteJob STOP = new WriteJob(null, null);

        @Nullable
        final byte[] data;
        @Nullable
        final BleValueObserver<Void> observer;
        final AtomicBoolean cancelled = new AtomicBoolean();

        private WriteJob(@Nullable byte[] data, @Nullable BleValueObserver<Void> observer) {
            this.data = data;
            this.observer = observer;
        }
    }

    private final class ChannelHandleImpl implements BleL2capChannelHandle {
        private final String opId;
        private final BleDeviceId deviceId;
        private final int psm;
        private final YoBleL2capPlatform.SocketConnection socket;
        private final Object writeLock = new Object();
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private final AtomicBoolean closedByLocal = new AtomicBoolean(false);
        private final AtomicBoolean closedNotified = new AtomicBoolean(false);
        private final AtomicLong writeSeq = new AtomicLong();
        private final LinkedBlockingQueue<WriteJob> writeQueue = new LinkedBlockingQueue<>();
        private final Thread writer;

        ChannelHandleImpl(@NonNull String opId,
                          @NonNull BleDeviceId deviceId,
                          int psm,
                          @NonNull YoBleL2capPlatform.SocketConnection socket) {
            this.opId = opId;
            this.deviceId = deviceId;
            this.psm = psm;
            this.socket = socket;
            this.writer = new Thread(this::drainWrites, "yo-ble-l2cap-tx-" + psm);
            this.writer.setDaemon(true);
            this.writer.start();
        }

        @NonNull
        @Override
        public String operationId() {
            return opId;
        }

        @Override
        public int psm() {
            return psm;
        }

        @Override
        public boolean isOpen() {
            return !closed.get() && !released;
        }

        @NonNull
        @Override
        public Result<Void> write(@NonNull byte[] data) {
            if (!isOpen()) {
                return Result.failure(YoBleErrorMapper.failure(
                        BleErrorCode.ILLEGAL_STATE, MODULE,
                        "L2CAP channel is closed", deviceId));
            }
            byte[] copy = data.clone();
            synchronized (writeLock) {
                if (!isOpen()) {
                    return Result.failure(YoBleErrorMapper.failure(
                            BleErrorCode.ILLEGAL_STATE, MODULE,
                            "L2CAP channel closed before write", deviceId));
                }
                try {
                    socket.write(copy);
                    return Result.success(null);
                } catch (Throwable e) {
                    return Result.failure(YoBleErrorMapper.failure(
                            BleErrorCode.L2CAP_IO_FAILED, MODULE,
                            "L2CAP write failed: " + e.getMessage(),
                            e, BleFailure.NO_RAW_STATUS, deviceId, null, null));
                }
            }
        }

        @NonNull
        @Override
        public BleOperationHandle writeAsync(@NonNull byte[] data, @NonNull BleValueObserver<Void> observer) {
            String writeId = opId + "-tx-" + writeSeq.incrementAndGet();
            if (!isOpen()) {
                BleFailure failure = YoBleErrorMapper.failure(
                        BleErrorCode.ILLEGAL_STATE, MODULE, "L2CAP channel is closed", deviceId);
                runtime.callback(() -> observer.onFailed(failure));
                return BleOperationHandle.completed(writeId);
            }
            if (writeQueue.size() >= 32) {
                BleFailure failure = YoBleErrorMapper.failure(
                        BleErrorCode.L2CAP_IO_FAILED, MODULE, "L2CAP write queue is full", deviceId);
                runtime.callback(() -> observer.onFailed(failure));
                return BleOperationHandle.completed(writeId);
            }
            WriteJob job = new WriteJob(data.clone(), observer);
            writeQueue.offer(job);
            return new BleOperationHandle(writeId, () -> job.cancelled.set(true));
        }

        private void drainWrites() {
            while (!closed.get()) {
                WriteJob job;
                try {
                    job = writeQueue.poll(200, TimeUnit.MILLISECONDS);
                } catch (InterruptedException interrupted) {
                    return;
                }
                if (job == null || job.data == null || job.cancelled.get()) {
                    if (job != null && job.data == null) {
                        return;
                    }
                    continue;
                }
                Result<Void> result = write(job.data);
                if (result.isSuccess()) {
                    runtime.callback(() -> job.observer.onSuccess(null));
                } else {
                    BleFailure failure = result.getFailure() instanceof BleFailure
                            ? (BleFailure) result.getFailure()
                            : YoBleErrorMapper.failure(BleErrorCode.L2CAP_IO_FAILED, MODULE, "L2CAP write failed", deviceId);
                    runtime.callback(() -> job.observer.onFailed(failure));
                }
            }
        }

        @Override
        public void close() {
            closedByLocal.set(true);
            closeInternal(false, null);
        }

        void closeInternal(boolean notifyObserver, BleL2capChannelObserver observer) {
            if (closed.compareAndSet(false, true)) {
                activeChannels.remove(this);
                writeQueue.offer(WriteJob.STOP);
                writer.interrupt();
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
                timeline.record(MODULE, "L2CAP channel closed device=" + deviceId + " psm=" + psm);
            }
            if (notifyObserver && observer != null && closedNotified.compareAndSet(false, true)) {
                runtime.callback(observer::onChannelClosed);
            }
        }
    }
}
