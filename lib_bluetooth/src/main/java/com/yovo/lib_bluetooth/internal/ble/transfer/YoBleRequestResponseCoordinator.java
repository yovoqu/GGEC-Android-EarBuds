package com.yovo.lib_bluetooth.internal.ble.transfer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.ble.error.YoBleErrorMapper;
import com.yovo.lib_bluetooth.internal.ble.gatt.YoBleGattOperationHandler;
import com.yovo.lib_bluetooth.internal.ble.gatt.YoBleNotificationRouter;
import com.yovo.lib_bluetooth.internal.ble.gatt.YoBleSessionContext;
import com.yovo.lib_bluetooth.internal.common.concurrency.YoTimeoutHandle;
import com.yovo.lib_bluetooth.model.ble.BleDataMerger;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleErrorCode;
import com.yovo.lib_bluetooth.model.ble.BleFailure;
import com.yovo.lib_bluetooth.model.ble.BleGattObserver;
import com.yovo.lib_bluetooth.model.ble.BleGattTarget;
import com.yovo.lib_bluetooth.model.ble.BleMergeResult;
import com.yovo.lib_bluetooth.model.ble.BleOperationHandle;
import com.yovo.lib_bluetooth.model.ble.BleRequestResponseConfig;
import com.yovo.lib_bluetooth.model.ble.BleValueObserver;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/**
 * 原子化请求-响应协同器（先注册监听再写入触发指令，支持 {@link BleDataMerger} 流式合包与超时清理）。
 */
public final class YoBleRequestResponseCoordinator {

    private static final String MODULE = "ble-req-resp";

    private final YoBleSessionContext context;
    private final YoBleGattOperationHandler gattHandler;
    private final AtomicLong seq = new AtomicLong();
    private final Map<BleGattTarget, Deque<LaneItem>> lanes = new LinkedHashMap<>();
    private final Set<BleGattTarget> laneBusy = new HashSet<>();

    public YoBleRequestResponseCoordinator(@NonNull YoBleSessionContext context,
                                           @NonNull YoBleGattOperationHandler gattHandler) {
        this.context = context;
        this.gattHandler = gattHandler;
    }

    @NonNull
    public BleOperationHandle sendAndWait(@NonNull BleRequestResponseConfig config,
                                          @NonNull BleValueObserver<byte[]> observer) {
        context.runtime().checkStateThread();
        if (!context.isReady()) {
            String opId = "ble-req-resp-" + seq.incrementAndGet();
            BleFailure f = YoBleErrorMapper.failure(
                    BleErrorCode.CONNECTION_NOT_READY, MODULE,
                    "BLE connection is not READY for sendAndWait", context.deviceId());
            context.runtime().callback(() -> observer.onFailed(f));
            return BleOperationHandle.completed(opId);
        }
        if (config.responseMatcher() == null) {
            BleGattTarget key = config.responseTarget().withoutDescriptor();
            if (!laneBusy.add(key)) {
                LaneItem item = new LaneItem(config, observer);
                lanes.computeIfAbsent(key, ignored -> new ArrayDeque<>()).add(item);
                return new BleOperationHandle(item.opId, () -> context.runtime().state(() -> cancelLaneItem(key, item)));
            }
            return beginSendAndWait(config, observer, () -> releaseLane(key));
        }
        return beginSendAndWait(config, observer, () -> { });
    }

    private BleOperationHandle beginSendAndWait(@NonNull BleRequestResponseConfig config,
                                                @NonNull BleValueObserver<byte[]> observer,
                                                @NonNull Runnable onFinished) {
        String opId = "ble-req-resp-" + seq.incrementAndGet();
        if (!context.isReady()) {
            onFinished.run();
            BleFailure f = YoBleErrorMapper.failure(
                    BleErrorCode.CONNECTION_NOT_READY, MODULE,
                    "BLE connection is not READY for sendAndWait", context.deviceId());
            context.runtime().callback(() -> observer.onFailed(f));
            return BleOperationHandle.completed(opId);
        }

        AtomicBoolean finished = new AtomicBoolean(false);
        AtomicReference<BleOperationHandle> writeHandleRef = new AtomicReference<>();
        AtomicReference<YoTimeoutHandle> timeoutRef = new AtomicReference<>();

        final byte[][] accumulated = {new byte[0]};
        final int[] chunkIndex = {0};

        YoBleNotificationRouter.RawPacketInterceptor[] interceptorHolder = new YoBleNotificationRouter.RawPacketInterceptor[1];
        AtomicBoolean cleaned = new AtomicBoolean(false);
        Runnable cleanup = () -> {
            if (!cleaned.compareAndSet(false, true)) {
                return;
            }
            context.runtime().state(() -> {
                YoTimeoutHandle t = timeoutRef.getAndSet(null);
                if (t != null) {
                    t.cancel();
                }
                if (interceptorHolder[0] != null) {
                    context.notificationRouter().unregisterInterceptor(
                            config.responseTarget(), interceptorHolder[0]);
                }
                context.notificationRouter().releaseHold(config.responseTarget());
                onFinished.run();
            });
        };

        interceptorHolder[0] = (sourceTarget, incomingChunk) -> {
            if (finished.get()) {
                return;
            }
            byte[] candidatePayload;
            BleDataMerger merger = config.dataMerger();
            if (merger != null) {
                BleDataMerger.Output output;
                try {
                    output = merger.merge(accumulated[0], incomingChunk, chunkIndex[0]++);
                } catch (Throwable thrown) {
                    if (finished.compareAndSet(false, true)) {
                        cleanup.run();
                        BleFailure f = YoBleErrorMapper.failure(
                                BleErrorCode.PAYLOAD_MERGE_FAILED, MODULE,
                                "DataMerger threw", thrown, BleFailure.NO_RAW_STATUS,
                                context.deviceId(), config.responseTarget(), null);
                        context.runtime().callback(() -> observer.onFailed(f));
                    }
                    return;
                }
                if (output.status() == BleMergeResult.INVALID) {
                    if (finished.compareAndSet(false, true)) {
                        cleanup.run();
                        BleFailure f = YoBleErrorMapper.failure(
                                BleErrorCode.PAYLOAD_MERGE_FAILED, MODULE,
                                "DataMerger rejected incoming response packet",
                                context.deviceId());
                        context.runtime().callback(() -> observer.onFailed(f));
                    }
                    return;
                }
                accumulated[0] = output.combined();
                if (output.status() == BleMergeResult.NEED_MORE) {
                    return;
                }
                candidatePayload = output.combined();
            } else {
                candidatePayload = incomingChunk.clone();
            }

            Predicate<byte[]> matcher = config.responseMatcher();
            boolean matched;
            try {
                matched = matcher == null || matcher.test(candidatePayload);
            } catch (Throwable thrown) {
                if (finished.compareAndSet(false, true)) {
                    cleanup.run();
                    BleFailure f = YoBleErrorMapper.failure(
                            BleErrorCode.PAYLOAD_MERGE_FAILED, MODULE,
                            "response matcher threw", thrown, BleFailure.NO_RAW_STATUS,
                            context.deviceId(), config.responseTarget(), null);
                    context.runtime().callback(() -> observer.onFailed(f));
                }
                return;
            }
            if (matched) {
                if (finished.compareAndSet(false, true)) {
                    cleanup.run();
                    byte[] finalBytes = candidatePayload.clone();
                    context.runtime().callback(() -> observer.onSuccess(finalBytes));
                }
            } else if (merger != null) {
                // 该帧不匹配本请求（多路 Notify 复用场景），重置合包缓冲区等待下一帧
                accumulated[0] = new byte[0];
                chunkIndex[0] = 0;
            }
        };

        // 关键顺序：先挂载响应拦截器，确保触发写包发出后即刻能接住极速响应
        context.notificationRouter().registerInterceptor(
                config.responseTarget(), interceptorHolder[0]);

        YoTimeoutHandle overallTimeout = context.runtime().scheduler().schedule(
                () -> context.runtime().state(() -> {
                    if (finished.compareAndSet(false, true)) {
                        cleanup.run();
                        BleOperationHandle wh = writeHandleRef.get();
                        if (wh != null) {
                            wh.cancel();
                        }
                        BleFailure timeoutErr = YoBleErrorMapper.failure(
                                BleErrorCode.GATT_OPERATION_TIMEOUT, MODULE,
                                "sendAndWait timed out after " + config.timeoutMillis() + "ms",
                                null, BleFailure.NO_RAW_STATUS, context.deviceId(),
                                config.responseTarget(), null);
                        context.runtime().callback(() -> observer.onFailed(timeoutErr));
                    }
                }), config.timeoutMillis());
        timeoutRef.set(overallTimeout);

        context.notificationRouter().ensureEnabled(
                config.responseTarget(),
                config.setupMode(),
                () -> {
                    if (finished.get()) {
                        return;
                    }
                    BleOperationHandle wh = gattHandler.write(
                            config.requestTarget(),
                            config.requestPayload(),
                            config.writeType(),
                            config.priority(),
                            new BleGattObserver() {
                                @Override
                                public void onWrite(@NonNull BleDeviceId deviceId,
                                                    @NonNull BleGattTarget target) {
                                    // 写入成功，继续等待拦截器捕获匹配的 Notify 响应
                                }

                                @Override
                                public void onFailed(@NonNull BleFailure failure) {
                                    if (finished.compareAndSet(false, true)) {
                                        cleanup.run();
                                        context.runtime().callback(() -> observer.onFailed(failure));
                                    }
                                }
                            });
                    writeHandleRef.set(wh);
                    if (finished.get()) {
                        wh.cancel();
                    }
                },
                setupFailure -> {
                    if (finished.compareAndSet(false, true)) {
                        cleanup.run();
                        context.runtime().callback(() -> observer.onFailed(setupFailure));
                    }
                });

        return new BleOperationHandle(opId, () -> {
            if (finished.compareAndSet(false, true)) {
                cleanup.run();
                BleOperationHandle wh = writeHandleRef.get();
                if (wh != null) {
                    wh.cancel();
                }
                BleFailure cancelledErr = YoBleErrorMapper.failure(
                        BleErrorCode.OPERATION_CANCELLED, MODULE,
                        "sendAndWait cancelled: " + opId, context.deviceId());
                context.runtime().callback(() -> observer.onFailed(cancelledErr));
            }
        });
    }

    private void releaseLane(@NonNull BleGattTarget key) {
        Deque<LaneItem> queue = lanes.get(key);
        while (queue != null && !queue.isEmpty()) {
            LaneItem next = queue.poll();
            if (next == null || next.cancelled.get()) {
                continue;
            }
            next.started = beginSendAndWait(next.config, next.observer, () -> releaseLane(key));
            return;
        }
        laneBusy.remove(key);
        lanes.remove(key);
    }

    private void cancelLaneItem(@NonNull BleGattTarget key, @NonNull LaneItem item) {
        if (!item.cancelled.compareAndSet(false, true)) {
            return;
        }
        Deque<LaneItem> queue = lanes.get(key);
        if (queue != null) {
            queue.remove(item);
        }
        BleOperationHandle started = item.started;
        if (started != null) {
            started.cancel();
            return;
        }
        BleFailure cancelled = YoBleErrorMapper.failure(
                BleErrorCode.OPERATION_CANCELLED, MODULE,
                "sendAndWait cancelled: " + item.opId, context.deviceId());
        context.runtime().callback(() -> item.observer.onFailed(cancelled));
    }

    private final class LaneItem {
        final String opId = "ble-req-resp-" + seq.incrementAndGet();
        @NonNull
        final BleRequestResponseConfig config;
        @NonNull
        final BleValueObserver<byte[]> observer;
        final AtomicBoolean cancelled = new AtomicBoolean();
        volatile BleOperationHandle started;

        LaneItem(@NonNull BleRequestResponseConfig config, @NonNull BleValueObserver<byte[]> observer) {
            this.config = config;
            this.observer = observer;
        }
    }
}
