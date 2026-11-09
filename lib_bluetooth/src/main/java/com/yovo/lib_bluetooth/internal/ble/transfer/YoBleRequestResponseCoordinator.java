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

    public YoBleRequestResponseCoordinator(@NonNull YoBleSessionContext context,
                                           @NonNull YoBleGattOperationHandler gattHandler) {
        this.context = context;
        this.gattHandler = gattHandler;
    }

    @NonNull
    public BleOperationHandle sendAndWait(@NonNull BleRequestResponseConfig config,
                                          @NonNull BleValueObserver<byte[]> observer) {
        String opId = "ble-req-resp-" + seq.incrementAndGet();
        if (!context.isReady()) {
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
        Runnable cleanup = () -> {
            YoTimeoutHandle t = timeoutRef.getAndSet(null);
            if (t != null) {
                t.cancel();
            }
            if (interceptorHolder[0] != null) {
                context.notificationRouter().unregisterInterceptor(
                        config.responseTarget(), interceptorHolder[0]);
            }
        };

        interceptorHolder[0] = (sourceTarget, incomingChunk) -> {
            if (finished.get()) {
                return;
            }
            byte[] candidatePayload;
            BleDataMerger merger = config.dataMerger();
            if (merger != null) {
                BleDataMerger.Output output = merger.merge(
                        accumulated[0], incomingChunk, chunkIndex[0]++);
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
            if (matcher == null || matcher.test(candidatePayload)) {
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
}
