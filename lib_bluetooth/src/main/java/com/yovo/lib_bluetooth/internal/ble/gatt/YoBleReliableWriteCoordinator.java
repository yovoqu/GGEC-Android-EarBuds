package com.yovo.lib_bluetooth.internal.ble.gatt;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.ble.error.YoBleErrorMapper;
import com.yovo.lib_bluetooth.internal.ble.gatt.YoBleGattOperationQueue.CompletionHandler;
import com.yovo.lib_bluetooth.internal.ble.gatt.YoBleGattOperationQueue.OpKind;
import com.yovo.lib_bluetooth.internal.ble.gatt.YoBleGattOperationQueue.StartResult;
import com.yovo.lib_bluetooth.model.ble.BleErrorCode;
import com.yovo.lib_bluetooth.model.ble.BleFailure;
import com.yovo.lib_bluetooth.model.ble.BleGattObserver;
import com.yovo.lib_bluetooth.model.ble.BleOperationHandle;
import com.yovo.lib_bluetooth.model.ble.BleOperationPriority;
import com.yovo.lib_bluetooth.model.common.Result;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 可靠写。begin 以普通优先级进入队列，保证排在它后面的事务写不会先发出。
 * 回显不一致或事务写失败会立刻 abort，并取消尚未发出的事务写。
 */
public final class YoBleReliableWriteCoordinator {

    private static final String MODULE = "ble-reliable-write";

    private enum Tx {
        IDLE, BEGIN_PENDING, OPEN, COMMITTING, ABORTING
    }

    @NonNull
    private final YoBleSessionContext context;
    private final AtomicBoolean openToken = new AtomicBoolean();
    private final AtomicLong seq = new AtomicLong();
    @NonNull
    private final ArrayDeque<byte[]> echoes = new ArrayDeque<>();
    @NonNull
    private Tx tx = Tx.IDLE;
    @Nullable
    private BleFailure poison;

    public YoBleReliableWriteCoordinator(@NonNull YoBleSessionContext context) {
        this.context = context;
    }

    /** 可在任意线程调用：用令牌拒绝重复 begin，真正入队仍在 state 线程。 */
    @NonNull
    public Result<Void> beginFromAnyThread() {
        if (!openToken.compareAndSet(false, true)) {
            return Result.failure(YoBleErrorMapper.failure(
                    BleErrorCode.ILLEGAL_STATE, MODULE, "reliable write already active", context.deviceId()));
        }
        context.runtime().state(this::enqueueBegin);
        return Result.success(null);
    }

    private void enqueueBegin() {
        if (!context.isReady()) {
            failBegin(YoBleErrorMapper.failure(
                    BleErrorCode.CONNECTION_NOT_READY, MODULE, "not ready for reliable write", context.deviceId()));
            return;
        }
        tx = Tx.BEGIN_PENDING;
        poison = null;
        String opId = "ble-rel-begin-" + seq.incrementAndGet();
        context.operationQueue().enqueue(opId, OpKind.RELIABLE_BEGIN, null, BleOperationPriority.NORMAL, 0L,
                () -> {
                    YoBleGattTransport transport = context.transport();
                    if (transport == null || !transport.beginReliableWrite()) {
                        return StartResult.failed(YoBleErrorMapper.failure(
                                BleErrorCode.WRITE_FAILED, MODULE, "beginReliableWrite rejected", context.deviceId()));
                    }
                    return StartResult.done(null);
                },
                new CompletionHandler() {
                    @Override
                    public void onSuccess(@Nullable Object payload) {
                        tx = Tx.OPEN;
                    }

                    @Override
                    public void onCancelled() {
                        failBegin(YoBleErrorMapper.failure(
                                BleErrorCode.OPERATION_CANCELLED, MODULE, "begin cancelled", context.deviceId()));
                    }

                    @Override
                    public void onFailed(@NonNull BleFailure failure) {
                        failBegin(failure);
                    }
                });
    }

    /** 同步受理。重复 begin 返回失败，不入队。 */
    @NonNull
    public Result<Void> beginAccepted() {
        if (!context.isReady()) {
            return Result.failure(YoBleErrorMapper.failure(
                    BleErrorCode.CONNECTION_NOT_READY, MODULE, "not ready for reliable write", context.deviceId()));
        }
        if (!openToken.compareAndSet(false, true)) {
            return Result.failure(YoBleErrorMapper.failure(
                    BleErrorCode.ILLEGAL_STATE, MODULE, "reliable write already active", context.deviceId()));
        }
        tx = Tx.BEGIN_PENDING;
        poison = null;
        String opId = "ble-rel-begin-" + seq.incrementAndGet();
        context.operationQueue().enqueue(opId, OpKind.RELIABLE_BEGIN, null, BleOperationPriority.NORMAL, 0L,
                () -> {
                    YoBleGattTransport transport = context.transport();
                    if (transport == null || !transport.beginReliableWrite()) {
                        return StartResult.failed(YoBleErrorMapper.failure(
                                BleErrorCode.WRITE_FAILED, MODULE, "beginReliableWrite rejected", context.deviceId()));
                    }
                    return StartResult.done(null);
                },
                new CompletionHandler() {
                    @Override
                    public void onSuccess(@Nullable Object payload) {
                        tx = Tx.OPEN;
                    }

                    @Override
                    public void onCancelled() {
                        failBegin(YoBleErrorMapper.failure(
                                BleErrorCode.OPERATION_CANCELLED, MODULE, "begin cancelled", context.deviceId()));
                    }

                    @Override
                    public void onFailed(@NonNull BleFailure failure) {
                        failBegin(failure);
                    }
                });
        return Result.success(null);
    }

    public boolean markIfTransaction(@NonNull BleOperationPriority ignored) {
        return openToken.get();
    }

    public void recordExpectedEcho(@NonNull byte[] value) {
        if (tx == Tx.OPEN || tx == Tx.BEGIN_PENDING) {
            echoes.add(value.clone());
        }
    }

    @Nullable
    public BleFailure verifyEcho(@Nullable byte[] echoed) {
        if (echoes.isEmpty()) {
            return null;
        }
        byte[] expected = echoes.removeFirst();
        if (echoed != null && !Arrays.equals(expected, echoed)) {
            return YoBleErrorMapper.failure(BleErrorCode.RELIABLE_WRITE_MISMATCH, MODULE,
                    "reliable write echo mismatch", context.deviceId());
        }
        return null;
    }

    public void poison(@NonNull BleFailure failure) {
        if (tx == Tx.IDLE) {
            return;
        }
        if (poison == null) {
            poison = failure;
        }
        context.operationQueue().cancelPendingTransactional();
        YoBleGattTransport transport = context.transport();
        if (transport != null && (tx == Tx.OPEN || tx == Tx.BEGIN_PENDING)) {
            transport.abortReliableWrite();
            tx = Tx.ABORTING;
        }
    }

    @NonNull
    public BleOperationHandle execute(@NonNull BleGattObserver observer) {
        return finish(true, observer);
    }

    @NonNull
    public BleOperationHandle abort(@NonNull BleGattObserver observer) {
        context.operationQueue().cancelPendingTransactional();
        return finish(false, observer);
    }

    public void reset() {
        tx = Tx.IDLE;
        poison = null;
        echoes.clear();
        openToken.set(false);
    }

    @NonNull
    private BleOperationHandle finish(boolean commit, @NonNull BleGattObserver observer) {
        String opId = "ble-rel-" + (commit ? "exec-" : "abort-") + seq.incrementAndGet();
        if (!openToken.get()) {
            observer.onFailed(YoBleErrorMapper.failure(
                    BleErrorCode.ILLEGAL_STATE, MODULE, "no reliable write transaction", context.deviceId()));
            return BleOperationHandle.completed(opId);
        }
        if (commit && poison != null) {
            BleFailure stored = poison;
            reset();
            observer.onFailed(stored);
            return BleOperationHandle.completed(opId);
        }
        OpKind kind = commit ? OpKind.RELIABLE_EXECUTE : OpKind.RELIABLE_ABORT;
        BleOperationPriority priority = commit ? BleOperationPriority.NORMAL : BleOperationPriority.HIGH;
        tx = commit ? Tx.COMMITTING : Tx.ABORTING;
        context.operationQueue().enqueue(opId, kind, null, priority, context.operationTimeoutMillis(),
                () -> {
                    YoBleGattTransport transport = context.transport();
                    if (transport == null) {
                        return StartResult.failed(YoBleErrorMapper.failure(
                                BleErrorCode.CONNECTION_NOT_READY, MODULE, "transport closed", context.deviceId()));
                    }
                    if (commit) {
                        if (!transport.executeReliableWrite()) {
                            return StartResult.failed(YoBleErrorMapper.failure(
                                    BleErrorCode.WRITE_FAILED, MODULE, "executeReliableWrite rejected", context.deviceId()));
                        }
                    } else {
                        transport.abortReliableWrite();
                    }
                    return StartResult.pending();
                },
                new CompletionHandler() {
                    @Override
                    public void onSuccess(@Nullable Object payload) {
                        reset();
                        observer.onReliableWriteCompleted(context.deviceId(), commit);
                    }

                    @Override
                    public void onCancelled() {
                        onFailed(YoBleErrorMapper.failure(
                                BleErrorCode.OPERATION_CANCELLED, MODULE, "reliable write cancelled", context.deviceId()));
                    }

                    @Override
                    public void onFailed(@NonNull BleFailure failure) {
                        reset();
                        observer.onFailed(failure);
                    }
                });
        return new BleOperationHandle(opId, () -> context.runtime().state(
                () -> context.operationQueue().cancel(opId)));
    }

    private void failBegin(@NonNull BleFailure failure) {
        poison = failure;
        tx = Tx.IDLE;
        openToken.set(false);
        context.operationQueue().cancelPendingTransactional();
    }
}
