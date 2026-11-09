package com.yovo.lib_bluetooth.internal.ble.channel;

import androidx.annotation.NonNull;

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
import com.yovo.lib_bluetooth.model.ble.BleOperationHandle;
import com.yovo.lib_bluetooth.model.common.Result;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
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
                                e, BleFailure.NO_RAW_STATUS, deviceId, null, null);
                        runtime.callback(() -> observer.onFailed(ioErr));
                        handle.closeInternal(true, observer);
                    }
                    return;
                }
            }
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

    @Override
    public void release() {
        released = true;
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

    private static void closeQuietly(@NonNull Closeable c) {
        try {
            c.close();
        } catch (Throwable ignored) {
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

        ChannelHandleImpl(@NonNull String opId,
                          @NonNull BleDeviceId deviceId,
                          int psm,
                          @NonNull YoBleL2capPlatform.SocketConnection socket) {
            this.opId = opId;
            this.deviceId = deviceId;
            this.psm = psm;
            this.socket = socket;
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

        @Override
        public void close() {
            closedByLocal.set(true);
            closeInternal(false, null);
        }

        void closeInternal(boolean notifyObserver, BleL2capChannelObserver observer) {
            if (closed.compareAndSet(false, true)) {
                activeChannels.remove(this);
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
