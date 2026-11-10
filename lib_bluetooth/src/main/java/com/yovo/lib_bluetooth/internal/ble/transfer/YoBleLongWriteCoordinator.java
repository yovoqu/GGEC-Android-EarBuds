package com.yovo.lib_bluetooth.internal.ble.transfer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.ble.error.YoBleErrorMapper;
import com.yovo.lib_bluetooth.internal.ble.gatt.YoBleGattOperationHandler;
import com.yovo.lib_bluetooth.internal.ble.gatt.YoBleSessionContext;
import com.yovo.lib_bluetooth.model.ble.BleDataSplitter;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleErrorCode;
import com.yovo.lib_bluetooth.model.ble.BleFailure;
import com.yovo.lib_bluetooth.model.ble.BleGattObserver;
import com.yovo.lib_bluetooth.model.ble.BleGattTarget;
import com.yovo.lib_bluetooth.model.ble.BleOperationHandle;
import com.yovo.lib_bluetooth.model.ble.BleOperationPriority;
import com.yovo.lib_bluetooth.model.ble.BleTransferProgressObserver;
import com.yovo.lib_bluetooth.model.ble.BleWriteType;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 大包自动分片长写协调器：支持按协商/覆盖 MTU 自动切片、自定义 {@link BleDataSplitter}、
 * 逐片发送确认、实时进度回调、瞬态写重试（GATT_BUSY）以及任意时刻中断取消。
 */
public final class YoBleLongWriteCoordinator {

    private static final String MODULE = "ble-long-write";
    private static final int DEFAULT_MAX_CHUNK_RETRIES = 2;

    private final YoBleSessionContext context;
    private final YoBleGattOperationHandler gattHandler;
    private final AtomicLong seq = new AtomicLong();

    public YoBleLongWriteCoordinator(@NonNull YoBleSessionContext context,
                                     @NonNull YoBleGattOperationHandler gattHandler) {
        this.context = context;
        this.gattHandler = gattHandler;
    }

    @NonNull
    public BleOperationHandle writeSplit(@NonNull BleGattTarget target,
                                         @NonNull byte[] value,
                                         @NonNull BleWriteType writeType,
                                         @Nullable BleDataSplitter customSplitter,
                                         @NonNull BleTransferProgressObserver observer) {
        String opId = "ble-long-write-" + seq.incrementAndGet();
        if (!context.isReady()) {
            BleFailure f = YoBleErrorMapper.failure(
                    BleErrorCode.CONNECTION_NOT_READY, MODULE,
                    "BLE connection is not READY for split write", context.deviceId());
            context.runtime().callback(() -> observer.onFailed(f));
            return BleOperationHandle.completed(opId);
        }

        int maxChunk = YoBleMtuPayloadCalculator.effectiveMaxChunkSize(
                context.currentMtu(), context.overriddenMtu(), writeType);
        BleDataSplitter splitter = customSplitter != null
                ? customSplitter : YoBleDefaultDataSplitter.INSTANCE;

        List<byte[]> chunks;
        try {
            chunks = splitter.split(value.clone(), maxChunk);
        } catch (Throwable t) {
            BleFailure f = YoBleErrorMapper.failure(
                    BleErrorCode.TRANSFER_FAILED, MODULE,
                    "DataSplitter threw exception", t,
                    BleFailure.NO_RAW_STATUS, context.deviceId(), target, null);
            context.runtime().callback(() -> observer.onFailed(f));
            return BleOperationHandle.completed(opId);
        }

        if (chunks.isEmpty()) {
            context.runtime().callback(() -> observer.onCompleted(context.deviceId(), target, 0L));
            return BleOperationHandle.completed(opId);
        }
        long totalBytes = 0L;
        for (byte[] chunk : chunks) {
            if (chunk.length > maxChunk) {
                BleFailure f = YoBleErrorMapper.failure(
                        BleErrorCode.INVALID_OFFSET_OR_LENGTH, MODULE,
                        "splitter chunk " + chunk.length + " exceeds " + maxChunk,
                        context.deviceId());
                context.runtime().callback(() -> observer.onFailed(f));
                return BleOperationHandle.completed(opId);
            }
            totalBytes += chunk.length;
        }
        int totalChunks = chunks.size();
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AtomicBoolean terminalEmitted = new AtomicBoolean(false);
        AtomicReference<BleOperationHandle> currentChunkHandle = new AtomicReference<>();

        context.timeline().record(MODULE, "start split write target=" + target
                + ", totalBytes=" + totalBytes + ", chunks=" + totalChunks
                + ", maxChunk=" + maxChunk);

        sendChunk(target, chunks, totalChunks, totalBytes, 0, 0L, 0,
                writeType, cancelled, terminalEmitted, currentChunkHandle, observer);

        return new BleOperationHandle(opId, () -> {
            if (cancelled.compareAndSet(false, true)) {
                BleOperationHandle active = currentChunkHandle.get();
                if (active != null) {
                    active.cancel();
                } else if (terminalEmitted.compareAndSet(false, true)) {
                    BleFailure cancelErr = YoBleErrorMapper.failure(
                            BleErrorCode.OPERATION_CANCELLED, MODULE,
                            "Split write cancelled: " + opId,
                            null, BleFailure.NO_RAW_STATUS, context.deviceId(), target, null);
                    context.runtime().callback(() -> observer.onFailed(cancelErr));
                }
                context.timeline().record(MODULE, "split write cancelled: " + opId);
            }
        });
    }

    private void sendChunk(@NonNull BleGattTarget target,
                           @NonNull List<byte[]> chunks,
                           int totalChunks,
                           long totalBytes,
                           int index,
                           long sentSoFar,
                           int attempt,
                           @NonNull BleWriteType writeType,
                           @NonNull AtomicBoolean cancelled,
                           @NonNull AtomicBoolean terminalEmitted,
                           @NonNull AtomicReference<BleOperationHandle> currentHandle,
                           @NonNull BleTransferProgressObserver observer) {
        if (cancelled.get()) {
            if (terminalEmitted.compareAndSet(false, true)) {
                context.runtime().callback(() -> observer.onFailed(YoBleErrorMapper.failure(
                        BleErrorCode.OPERATION_CANCELLED, MODULE,
                        "Split write cancelled at chunk " + index + "/" + totalChunks,
                        null, BleFailure.NO_RAW_STATUS, context.deviceId(), target, null)));
            }
            return;
        }
        if (!context.isReady()) {
            if (terminalEmitted.compareAndSet(false, true)) {
                context.runtime().callback(() -> observer.onFailed(YoBleErrorMapper.failure(
                        BleErrorCode.CONNECTION_NOT_READY, MODULE,
                        "BLE connection lost during split write at chunk " + index + "/" + totalChunks,
                        context.deviceId())));
            }
            return;
        }
        if (index >= totalChunks) {
            if (terminalEmitted.compareAndSet(false, true)) {
                context.timeline().record(MODULE, "split write completed totalBytes=" + totalBytes);
                context.runtime().callback(() -> observer.onCompleted(context.deviceId(), target, totalBytes));
            }
            return;
        }

        byte[] chunk = chunks.get(index);
        BleOperationHandle handle = gattHandler.write(
                target,
                chunk,
                writeType,
                BleOperationPriority.NORMAL,
                new BleGattObserver() {
                    @Override
                    public void onWrite(@NonNull BleDeviceId deviceId, @NonNull BleGattTarget t) {
                        if (cancelled.get()) {
                            if (terminalEmitted.compareAndSet(false, true)) {
                                context.runtime().callback(() -> observer.onFailed(YoBleErrorMapper.failure(
                                        BleErrorCode.OPERATION_CANCELLED, MODULE,
                                        "Split write cancelled after chunk " + index + "/" + totalChunks,
                                        null, BleFailure.NO_RAW_STATUS, context.deviceId(), target, null)));
                            }
                            return;
                        }
                        long updatedSent = sentSoFar + chunk.length;
                        context.runtime().callback(() -> observer.onChunkSent(
                                deviceId, target, index, totalChunks, updatedSent, totalBytes));
                        if (index + 1 >= totalChunks) {
                            if (terminalEmitted.compareAndSet(false, true)) {
                                context.timeline().record(MODULE,
                                        "split write completed totalBytes=" + totalBytes);
                                context.runtime().callback(() ->
                                        observer.onCompleted(deviceId, target, totalBytes));
                            }
                        } else {
                            context.runtime().state(() -> sendChunk(target, chunks, totalChunks, totalBytes,
                                    index + 1, updatedSent, 0, writeType,
                                    cancelled, terminalEmitted, currentHandle, observer));
                        }
                    }

                    @Override
                    public void onFailed(@NonNull BleFailure failure) {
                        if (cancelled.get() || failure.errorCode() == BleErrorCode.OPERATION_CANCELLED) {
                            if (terminalEmitted.compareAndSet(false, true)) {
                                context.runtime().callback(() -> observer.onFailed(failure));
                            }
                            return;
                        }
                        if ((failure.errorCode() == BleErrorCode.GATT_BUSY || failure.retryable())
                                && attempt < DEFAULT_MAX_CHUNK_RETRIES
                                && context.isReady()) {
                            context.timeline().record(MODULE,
                                    "retrying chunk " + index + " attempt=" + (attempt + 1)
                                            + " after " + failure.errorCode());
                            context.runtime().state(() -> sendChunk(target, chunks, totalChunks, totalBytes,
                                    index, sentSoFar, attempt + 1, writeType,
                                    cancelled, terminalEmitted, currentHandle, observer));
                            return;
                        }
                        if (terminalEmitted.compareAndSet(false, true)) {
                            context.timeline().record(MODULE,
                                    "split write failed at chunk " + index + ": " + failure.errorCode());
                            context.runtime().callback(() -> observer.onFailed(failure));
                        }
                    }
                });
        currentHandle.set(handle);
        if (cancelled.get()) {
            handle.cancel();
        }
    }
}
