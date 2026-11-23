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

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 大包自动分片长写协调器：支持按协商/覆盖 MTU 自动切片、自定义 {@link BleDataSplitter}、
 * 逐片发送确认、实时进度回调、瞬态写重试（GATT_BUSY）以及任意时刻中断取消。
 *
 * <p>默认分片器走<b>动态逐片</b>路径：不预切，每片发送前按<b>当前</b> MTU 重新计算片大小，
 * 因此传输过程中协商/覆盖 MTU 变化可即时生效。自定义分片器或空载荷保持原有的预切分片路径，
 * 行为完全不变。</p>
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

        // 自定义分片器或空载荷：保持原有预切分片路径，行为不变。
        if (customSplitter != null || value.length == 0) {
            BleDataSplitter splitter = customSplitter != null
                    ? customSplitter : YoBleDefaultDataSplitter.INSTANCE;
            return writeSplitPrecomputed(opId, target, value, writeType, splitter, observer);
        }

        // 默认分片器且非空：动态逐片，每片按当前 MTU 重新计算片大小。
        return writeSplitDynamic(opId, target, value, writeType, observer);
    }

    // ── 预切分片路径（自定义 splitter / 空载荷，语义与改造前完全一致） ──────────────

    @NonNull
    private BleOperationHandle writeSplitPrecomputed(@NonNull String opId,
                                                     @NonNull BleGattTarget target,
                                                     @NonNull byte[] value,
                                                     @NonNull BleWriteType writeType,
                                                     @NonNull BleDataSplitter splitter,
                                                     @NonNull BleTransferProgressObserver observer) {
        int maxChunk = YoBleMtuPayloadCalculator.effectiveMaxChunkSize(
                context.currentMtu(), context.overriddenMtu(), writeType);

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

    // ── 动态逐片路径（默认分片器 + 非空载荷，每片按当前 MTU 重算片大小） ─────────────

    @NonNull
    private BleOperationHandle writeSplitDynamic(@NonNull String opId,
                                                 @NonNull BleGattTarget target,
                                                 @NonNull byte[] value,
                                                 @NonNull BleWriteType writeType,
                                                 @NonNull BleTransferProgressObserver observer) {
        byte[] payload = value.clone();
        int totalBytes = payload.length;
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AtomicBoolean terminalEmitted = new AtomicBoolean(false);
        AtomicReference<BleOperationHandle> currentChunkHandle = new AtomicReference<>();

        context.timeline().record(MODULE, "start dynamic split write target=" + target
                + ", totalBytes=" + totalBytes);

        sendChunkDynamic(target, payload, 0, 0, 0, writeType,
                cancelled, terminalEmitted, currentChunkHandle, observer);

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

    private void sendChunkDynamic(@NonNull BleGattTarget target,
                                  @NonNull byte[] payload,
                                  int index,
                                  int sentSoFar,
                                  int attempt,
                                  @NonNull BleWriteType writeType,
                                  @NonNull AtomicBoolean cancelled,
                                  @NonNull AtomicBoolean terminalEmitted,
                                  @NonNull AtomicReference<BleOperationHandle> currentHandle,
                                  @NonNull BleTransferProgressObserver observer) {
        int totalBytes = payload.length;
        if (cancelled.get()) {
            if (terminalEmitted.compareAndSet(false, true)) {
                context.runtime().callback(() -> observer.onFailed(YoBleErrorMapper.failure(
                        BleErrorCode.OPERATION_CANCELLED, MODULE,
                        "Split write cancelled at offset " + sentSoFar + "/" + totalBytes,
                        null, BleFailure.NO_RAW_STATUS, context.deviceId(), target, null)));
            }
            return;
        }
        if (!context.isReady()) {
            if (terminalEmitted.compareAndSet(false, true)) {
                context.runtime().callback(() -> observer.onFailed(YoBleErrorMapper.failure(
                        BleErrorCode.CONNECTION_NOT_READY, MODULE,
                        "BLE connection lost during split write at offset " + sentSoFar + "/" + totalBytes,
                        context.deviceId())));
            }
            return;
        }
        if (sentSoFar >= totalBytes) {
            if (terminalEmitted.compareAndSet(false, true)) {
                context.timeline().record(MODULE, "split write completed totalBytes=" + totalBytes);
                context.runtime().callback(() -> observer.onCompleted(context.deviceId(), target, totalBytes));
            }
            return;
        }

        // 关键：每片发送前按当前（协商/覆盖）MTU 重新计算片大小，传输中 MTU 变化即时生效。
        int maxChunk = YoBleMtuPayloadCalculator.effectiveMaxChunkSize(
                context.currentMtu(), context.overriddenMtu(), writeType);
        if (maxChunk <= 0) {
            if (terminalEmitted.compareAndSet(false, true)) {
                BleFailure f = YoBleErrorMapper.failure(
                        BleErrorCode.INVALID_OFFSET_OR_LENGTH, MODULE,
                        "invalid MTU: effective max chunk " + maxChunk
                                + " at offset " + sentSoFar, context.deviceId());
                context.timeline().record(MODULE, "split write failed: invalid maxChunk=" + maxChunk);
                context.runtime().callback(() -> observer.onFailed(f));
            }
            return;
        }

        int end = YoBleMtuPayloadCalculator.chunkEnd(totalBytes, sentSoFar, maxChunk);
        byte[] chunk = Arrays.copyOfRange(payload, sentSoFar, end);
        // 仅用于进度展示的近似总片数（MTU 变化时实时更新）。
        int totalChunksEstimate = (int) Math.ceil((double) totalBytes / (double) maxChunk);

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
                                        "Split write cancelled after offset " + sentSoFar + "/" + totalBytes,
                                        null, BleFailure.NO_RAW_STATUS, context.deviceId(), target, null)));
                            }
                            return;
                        }
                        int updatedSent = sentSoFar + chunk.length;
                        context.runtime().callback(() -> observer.onChunkSent(
                                deviceId, target, index, totalChunksEstimate, updatedSent, totalBytes));
                        if (updatedSent >= totalBytes) {
                            if (terminalEmitted.compareAndSet(false, true)) {
                                context.timeline().record(MODULE,
                                        "split write completed totalBytes=" + totalBytes);
                                context.runtime().callback(() ->
                                        observer.onCompleted(deviceId, target, totalBytes));
                            }
                        } else {
                            context.runtime().state(() -> sendChunkDynamic(target, payload,
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
                                    "retrying chunk at offset " + sentSoFar + " attempt=" + (attempt + 1)
                                            + " after " + failure.errorCode());
                            context.runtime().state(() -> sendChunkDynamic(target, payload,
                                    index, sentSoFar, attempt + 1, writeType,
                                    cancelled, terminalEmitted, currentHandle, observer));
                            return;
                        }
                        if (terminalEmitted.compareAndSet(false, true)) {
                            context.timeline().record(MODULE,
                                    "split write failed at offset " + sentSoFar + ": " + failure.errorCode());
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
