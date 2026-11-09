package com.yovo.lib_bluetooth.internal.ble.gatt;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.ble.error.YoBleErrorMapper;
import com.yovo.lib_bluetooth.internal.ble.runtime.YoBleRuntime;
import com.yovo.lib_bluetooth.internal.common.concurrency.YoTimeoutHandle;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleErrorCode;
import com.yovo.lib_bluetooth.model.ble.BleFailure;
import com.yovo.lib_bluetooth.model.ble.BleGattTarget;
import com.yovo.lib_bluetooth.model.ble.BleOperationPriority;

import java.util.Comparator;
import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * 每连接独立的串行 GATT 操作队列：
 * <ol>
 *   <li>严格确保任意时刻仅 1 个 {@code inFlight} GATT PDU；</li>
 *   <li>支持 {@link BleOperationPriority} 优先级插队，同优先级 FIFO；</li>
 *   <li>支持未出队请求同步取消与在途请求超时熔断，永不死锁后续队列。</li>
 * </ol>
 */
public final class YoBleGattOperationQueue {

    public enum OpKind {
        DISCOVER_SERVICES,
        READ_CHAR,
        WRITE_CHAR,
        READ_DESC,
        WRITE_DESC,
        REQUEST_MTU,
        CONNECTION_PRIORITY,
        SET_PHY,
        READ_PHY,
        READ_RSSI,
        RELIABLE_EXECUTE,
        RELIABLE_ABORT
    }

    public interface TaskExecutor {
        /**
         * 执行底层请求。
         *
         * @return null 表示异步发起成功等待 GATT 回调；非 null 表示同步发起即失败
         */
        @Nullable
        BleFailure start();
    }

    public interface CompletionHandler {
        default void onSuccess(@Nullable Object payload) {
        }

        void onCancelled();

        void onFailed(@NonNull BleFailure failure);
    }

    public static final class QueuedOperation {
        final long sequenceId;
        @NonNull
        final String operationId;
        @NonNull
        final OpKind kind;
        @Nullable
        final BleGattTarget target;
        @NonNull
        final BleOperationPriority priority;
        final long timeoutMillis;
        @NonNull
        final TaskExecutor executor;
        @NonNull
        final CompletionHandler completionHandler;

        volatile boolean cancelled;
        volatile boolean finished;
        @Nullable
        YoTimeoutHandle timeoutHandle;
        @Nullable
        Consumer<Object> attachment;

        QueuedOperation(long sequenceId,
                        @NonNull String operationId,
                        @NonNull OpKind kind,
                        @Nullable BleGattTarget target,
                        @NonNull BleOperationPriority priority,
                        long timeoutMillis,
                        @NonNull TaskExecutor executor,
                        @NonNull CompletionHandler completionHandler) {
            this.sequenceId = sequenceId;
            this.operationId = operationId;
            this.kind = kind;
            this.target = target;
            this.priority = priority;
            this.timeoutMillis = timeoutMillis;
            this.executor = executor;
            this.completionHandler = completionHandler;
        }

        @NonNull
        public String operationId() {
            return operationId;
        }

        @NonNull
        public OpKind kind() {
            return kind;
        }

        @Nullable
        public BleGattTarget target() {
            return target;
        }

        public boolean isFinishedOrCancelled() {
            return finished || cancelled;
        }
    }

    private static final String MODULE = "ble-gatt-queue";

    private static final Comparator<QueuedOperation> COMPARATOR = (a, b) -> {
        int rankCmp = Integer.compare(a.priority.rank(), b.priority.rank());
        return rankCmp != 0 ? rankCmp : Long.compare(a.sequenceId, b.sequenceId);
    };

    private final BleDeviceId deviceId;
    private final YoBleRuntime runtime;
    private final int maxCapacity;
    private final AtomicLong seqGen = new AtomicLong();
    private final PriorityQueue<QueuedOperation> queue;

    @Nullable
    private QueuedOperation inFlight;

    public YoBleGattOperationQueue(@NonNull BleDeviceId deviceId,
                                   @NonNull YoBleRuntime runtime,
                                   int maxCapacity) {
        this.deviceId = deviceId;
        this.runtime = runtime;
        this.maxCapacity = Math.max(4, maxCapacity);
        this.queue = new PriorityQueue<>(16, COMPARATOR);
    }

    @NonNull
    public QueuedOperation enqueue(@NonNull String operationId,
                                   @NonNull OpKind kind,
                                   @Nullable BleGattTarget target,
                                   @NonNull BleOperationPriority priority,
                                   long timeoutMillis,
                                   @NonNull TaskExecutor executor,
                                   @NonNull CompletionHandler completionHandler) {
        QueuedOperation op = new QueuedOperation(
                seqGen.incrementAndGet(),
                operationId,
                kind,
                target,
                priority,
                timeoutMillis,
                executor,
                completionHandler);

        synchronized (this) {
            if (queue.size() >= maxCapacity) {
                op.finished = true;
                BleFailure full = YoBleErrorMapper.failure(
                        BleErrorCode.GATT_QUEUE_FULL,
                        MODULE,
                        "GATT operation queue is full (capacity=" + maxCapacity + ")",
                        null, BleFailure.NO_RAW_STATUS, deviceId, target,
                        "Wait for pending operations to finish or call clearPendingOperations()");
                runtime.state(() -> completionHandler.onFailed(full));
                return op;
            }
            queue.add(op);
        }
        drainNext();
        return op;
    }

    /** 取消指定 operationId 的排队中或在途操作；若从 pending 移除则立刻回报 CANCELLED。 */
    public boolean cancel(@NonNull String operationId) {
        QueuedOperation removedPending = null;
        QueuedOperation activeMatch = null;

        synchronized (this) {
            if (inFlight != null && inFlight.operationId.equals(operationId)) {
                if (!inFlight.finished && !inFlight.cancelled) {
                    inFlight.cancelled = true;
                    if (inFlight.timeoutHandle != null) {
                        inFlight.timeoutHandle.cancel();
                        inFlight.timeoutHandle = null;
                    }
                    activeMatch = inFlight;
                }
            } else {
                Iterator<QueuedOperation> it = queue.iterator();
                while (it.hasNext()) {
                    QueuedOperation candidate = it.next();
                    if (candidate.operationId.equals(operationId)) {
                        it.remove();
                        candidate.cancelled = true;
                        candidate.finished = true;
                        removedPending = candidate;
                        break;
                    }
                }
            }
        }

        if (removedPending != null) {
            QueuedOperation targetOp = removedPending;
            runtime.state(targetOp.completionHandler::onCancelled);
            return true;
        }
        if (activeMatch != null) {
            QueuedOperation targetOp = activeMatch;
            runtime.state(targetOp.completionHandler::onCancelled);
            return true;
        }
        return false;
    }

    @Nullable
    public QueuedOperation completeInFlightIfMatches(@NonNull OpKind kind) {
        QueuedOperation completed;
        synchronized (this) {
            if (inFlight == null || inFlight.kind != kind) {
                return null;
            }
            completed = releaseInFlightLocked();
        }
        drainNext();
        return completed != null && !completed.cancelled ? completed : null;
    }

    public boolean succeedInFlightIfMatches(@NonNull OpKind kind, @Nullable Object payload) {
        QueuedOperation op = completeInFlightIfMatches(kind);
        if (op == null) {
            return false;
        }
        runtime.state(() -> op.completionHandler.onSuccess(payload));
        return true;
    }

    public boolean failInFlightIfMatches(@NonNull OpKind kind, @NonNull BleFailure failure) {
        QueuedOperation op = completeInFlightIfMatches(kind);
        if (op == null) {
            return false;
        }
        runtime.state(() -> op.completionHandler.onFailed(failure));
        return true;
    }

    @Nullable
    public QueuedOperation completeCurrentInFlight() {
        QueuedOperation completed;
        synchronized (this) {
            completed = releaseInFlightLocked();
        }
        drainNext();
        return completed != null && !completed.cancelled ? completed : null;
    }

    @Nullable
    public synchronized QueuedOperation currentInFlight() {
        return inFlight;
    }

    public synchronized int pendingCount() {
        return queue.size();
    }

    public synchronized boolean isBusy() {
        return inFlight != null || !queue.isEmpty();
    }

    /** 清空全部 Pending 与当前 InFlight，返回共取消数量。 */
    public int clearAll(@NonNull BleFailure reason) {
        PriorityQueue<QueuedOperation> drained;
        QueuedOperation active;
        synchronized (this) {
            active = releaseInFlightLocked();
            drained = new PriorityQueue<>(queue);
            queue.clear();
        }
        int count = 0;
        if (active != null && !active.cancelled) {
            active.finished = true;
            count++;
            runtime.state(() -> active.completionHandler.onFailed(reason));
        }
        QueuedOperation op;
        while ((op = drained.poll()) != null) {
            if (!op.cancelled) {
                op.finished = true;
                count++;
                QueuedOperation ref = op;
                runtime.state(() -> ref.completionHandler.onFailed(reason));
            }
        }
        return count;
    }

    public void drainNext() {
        while (true) {
            QueuedOperation next;
            synchronized (this) {
                if (inFlight != null) {
                    return;
                }
                next = queue.poll();
                if (next == null) {
                    return;
                }
                if (next.cancelled || next.finished) {
                    continue;
                }
                inFlight = next;
                if (next.timeoutMillis > 0L) {
                    next.timeoutHandle = runtime.scheduler().schedule(
                            () -> runtime.state(() -> handleTimeout(next)), next.timeoutMillis);
                }
            }

            BleFailure immediateFailure;
            try {
                immediateFailure = next.executor.start();
            } catch (Throwable t) {
                immediateFailure = YoBleErrorMapper.failure(
                        BleErrorCode.INTERNAL_ERROR, MODULE,
                        "Unexpected exception starting GATT operation " + next.kind,
                        t, BleFailure.NO_RAW_STATUS, deviceId, next.target, null);
            }

            if (immediateFailure != null) {
                synchronized (this) {
                    if (inFlight == next) {
                        releaseInFlightLocked();
                    }
                }
                if (!next.cancelled) {
                    BleFailure failRef = immediateFailure;
                    runtime.state(() -> next.completionHandler.onFailed(failRef));
                }
                continue;
            }
            return;
        }
    }

    private void handleTimeout(@NonNull QueuedOperation timedOutOp) {
        boolean hit = false;
        synchronized (this) {
            if (inFlight == timedOutOp && !timedOutOp.finished) {
                releaseInFlightLocked();
                hit = true;
            }
        }
        if (hit) {
            if (!timedOutOp.cancelled) {
                BleFailure timeoutFailure = YoBleErrorMapper.failure(
                        BleErrorCode.GATT_OPERATION_TIMEOUT, MODULE,
                        "GATT operation timed out after " + timedOutOp.timeoutMillis
                                + "ms: " + timedOutOp.kind,
                        null, BleFailure.NO_RAW_STATUS, deviceId, timedOutOp.target,
                        "Increase operationTimeoutMillis or verify remote peripheral responsiveness");
                runtime.state(() -> timedOutOp.completionHandler.onFailed(timeoutFailure));
            }
            drainNext();
        }
    }

    @Nullable
    private QueuedOperation releaseInFlightLocked() {
        QueuedOperation op = inFlight;
        inFlight = null;
        if (op != null) {
            op.finished = true;
            if (op.timeoutHandle != null) {
                op.timeoutHandle.cancel();
                op.timeoutHandle = null;
            }
        }
        return op;
    }
}
