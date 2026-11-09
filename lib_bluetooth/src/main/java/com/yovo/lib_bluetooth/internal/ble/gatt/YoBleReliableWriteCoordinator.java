package com.yovo.lib_bluetooth.internal.ble.gatt;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.ble.error.YoBleErrorMapper;
import com.yovo.lib_bluetooth.model.ble.BleErrorCode;
import com.yovo.lib_bluetooth.model.ble.BleFailure;
import com.yovo.lib_bluetooth.model.ble.BleGattObserver;
import com.yovo.lib_bluetooth.model.ble.BleGattTarget;
import com.yovo.lib_bluetooth.model.ble.BleOperationHandle;
import com.yovo.lib_bluetooth.model.ble.BleOperationPriority;
import com.yovo.lib_bluetooth.model.common.Result;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * GATT Reliable Write 事务协调器：支持开启事务、回显校验写值一致性、提交与异常/断连自动回滚。
 */
public final class YoBleReliableWriteCoordinator {

    private static final String MODULE = "ble-reliable-write";

    private static final class ExpectedEcho {
        @NonNull
        final BleGattTarget target;
        @NonNull
        final byte[] payload;

        ExpectedEcho(@NonNull BleGattTarget target, @NonNull byte[] payload) {
            this.target = target;
            this.payload = payload;
        }
    }

    private final YoBleSessionContext context;
    private final AtomicLong seq = new AtomicLong();
    private final Deque<ExpectedEcho> expectedEchoes = new ArrayDeque<>();
    private volatile boolean inTransaction = false;

    public YoBleReliableWriteCoordinator(@NonNull YoBleSessionContext context) {
        this.context = context;
    }

    public boolean isInTransaction() {
        return inTransaction;
    }

    @NonNull
    public synchronized Result<Void> beginReliableWrite() {
        if (!context.isReady()) {
            return Result.failure(YoBleErrorMapper.failure(
                    BleErrorCode.CONNECTION_NOT_READY, MODULE,
                    "BLE connection is not READY for beginReliableWrite", context.deviceId()));
        }
        if (inTransaction) {
            return Result.failure(YoBleErrorMapper.failure(
                    BleErrorCode.ILLEGAL_STATE, MODULE,
                    "Reliable write transaction already active", context.deviceId()));
        }
        YoBleGattTransport transport = context.transport();
        if (transport == null || !transport.beginReliableWrite()) {
            return Result.failure(YoBleErrorMapper.failure(
                    BleErrorCode.WRITE_FAILED, MODULE,
                    "beginReliableWrite returned false", context.deviceId()));
        }
        expectedEchoes.clear();
        inTransaction = true;
        context.timeline().record(MODULE, "beginReliableWrite");
        return Result.success(null);
    }

    public synchronized void recordExpectedEcho(@NonNull BleGattTarget target,
                                                @NonNull byte[] payload) {
        if (inTransaction) {
            expectedEchoes.addLast(new ExpectedEcho(target, payload.clone()));
        }
    }

    public synchronized void popExpectedEcho(@NonNull BleGattTarget target) {
        if (!expectedEchoes.isEmpty() && expectedEchoes.peekLast().target.equals(target)) {
            expectedEchoes.removeLast();
        }
    }

    public synchronized boolean verifyEcho(@NonNull BleGattTarget target,
                                           @Nullable byte[] echoedValue) {
        ExpectedEcho expected = expectedEchoes.pollFirst();
        if (expected == null) {
            return true;
        }
        if (echoedValue == null) {
            return true; // 部分栈回调不携带写回显，放行由远端校验
        }
        return Arrays.equals(expected.payload, echoedValue);
    }

    @NonNull
    public BleOperationHandle executeReliableWrite(@NonNull BleGattObserver observer) {
        String opId = "ble-reliable-exec-" + seq.incrementAndGet();
        if (!context.isReady() || !inTransaction) {
            BleFailure f = YoBleErrorMapper.failure(
                    BleErrorCode.ILLEGAL_STATE, MODULE,
                    "No active reliable write transaction to execute", context.deviceId());
            context.runtime().callback(() -> observer.onFailed(f));
            return BleOperationHandle.completed(opId);
        }

        context.operationQueue().enqueue(
                opId,
                YoBleGattOperationQueue.OpKind.RELIABLE_EXECUTE,
                null,
                BleOperationPriority.HIGH,
                context.operationTimeoutMillis(),
                () -> {
                    YoBleGattTransport transport = context.transport();
                    if (transport == null || !transport.executeReliableWrite()) {
                        return YoBleErrorMapper.failure(
                                BleErrorCode.WRITE_FAILED, MODULE,
                                "executeReliableWrite returned false", context.deviceId());
                    }
                    return null;
                },
                new YoBleGattOperationQueue.CompletionHandler() {
                    @Override
                    public void onSuccess(@Nullable Object payload) {
                        reset();
                        context.timeline().record(MODULE, "executeReliableWrite committed");
                        context.runtime().callback(() ->
                                observer.onReliableWriteCompleted(context.deviceId(), true));
                    }

                    @Override
                    public void onCancelled() {
                        reset();
                        context.runtime().callback(() -> observer.onFailed(YoBleErrorMapper.failure(
                                BleErrorCode.OPERATION_CANCELLED, MODULE,
                                "executeReliableWrite cancelled", context.deviceId())));
                    }

                    @Override
                    public void onFailed(@NonNull BleFailure failure) {
                        reset();
                        context.runtime().callback(() -> observer.onFailed(failure));
                    }
                });

        return new BleOperationHandle(opId, () -> context.operationQueue().cancel(opId));
    }

    @NonNull
    public BleOperationHandle abortReliableWrite(@NonNull BleGattObserver observer) {
        String opId = "ble-reliable-abort-" + seq.incrementAndGet();
        if (!inTransaction) {
            BleFailure f = YoBleErrorMapper.failure(
                    BleErrorCode.ILLEGAL_STATE, MODULE,
                    "No active reliable write transaction to abort", context.deviceId());
            context.runtime().callback(() -> observer.onFailed(f));
            return BleOperationHandle.completed(opId);
        }

        context.operationQueue().enqueue(
                opId,
                YoBleGattOperationQueue.OpKind.RELIABLE_ABORT,
                null,
                BleOperationPriority.HIGH,
                context.operationTimeoutMillis(),
                () -> {
                    YoBleGattTransport transport = context.transport();
                    if (transport == null) {
                        return YoBleErrorMapper.failure(
                                BleErrorCode.CONNECTION_NOT_READY, MODULE,
                                "Transport closed", context.deviceId());
                    }
                    transport.abortReliableWrite();
                    return null;
                },
                new YoBleGattOperationQueue.CompletionHandler() {
                    @Override
                    public void onSuccess(@Nullable Object payload) {
                        reset();
                        context.timeline().record(MODULE, "abortReliableWrite completed");
                        context.runtime().callback(() ->
                                observer.onReliableWriteCompleted(context.deviceId(), false));
                    }

                    @Override
                    public void onCancelled() {
                        reset();
                        context.runtime().callback(() -> observer.onFailed(YoBleErrorMapper.failure(
                                BleErrorCode.OPERATION_CANCELLED, MODULE,
                                "abortReliableWrite cancelled", context.deviceId())));
                    }

                    @Override
                    public void onFailed(@NonNull BleFailure failure) {
                        reset();
                        context.runtime().callback(() -> observer.onFailed(failure));
                    }
                });

        return new BleOperationHandle(opId, () -> context.operationQueue().cancel(opId));
    }

    public synchronized void reset() {
        inTransaction = false;
        expectedEchoes.clear();
    }
}
