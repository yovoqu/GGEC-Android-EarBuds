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
import com.yovo.lib_bluetooth.provider.lib_common.log.YoBtLogger;

import java.util.Comparator;
import java.util.Iterator;
import java.util.PriorityQueue;

/**
 * 每连接一条 GATT 操作队列。任意时刻只有一个在途操作；完成按种类加目标匹配，
 * 取消或超时后槽位保留为僵尸，直到匹配的平台回调到达或宽限截止，避免迟到回调配错下一条操作。
 * 只在 state 线程调用（{@link #publishedPendingCount()} 除外）。
 */
public final class YoBleGattOperationQueue {

    private static final String TAG = "YoBleGattQueue";
    /** 取消或超时之后，等待迟到平台回调的宽限。 */
    public static final long STALE_CALLBACK_GRACE_MS = 5_000L;
    /** refreshDeviceCache 之后、重新发现服务之前的稳定等待。 */
    public static final long CACHE_REFRESH_SETTLE_MS = 200L;

    public enum OpKind {
        DISCOVER_SERVICES,
        REFRESH_CACHE,
        READ_CHAR,
        WRITE_CHAR,
        READ_DESC,
        WRITE_DESC,
        REQUEST_MTU,
        CONNECTION_PRIORITY,
        SET_PHY,
        READ_PHY,
        READ_RSSI,
        RELIABLE_BEGIN,
        RELIABLE_EXECUTE,
        RELIABLE_ABORT
    }

    /** {@link TaskExecutor#start()} 的结果：等待平台回调、立即成功，或立即失败。 */
    public static final class StartResult {
        private final boolean pending;
        @Nullable
        private final Object payload;
        @Nullable
        private final BleFailure failure;

        private StartResult(boolean pending, @Nullable Object payload, @Nullable BleFailure failure) {
            this.pending = pending;
            this.payload = payload;
            this.failure = failure;
        }

        @NonNull
        public static StartResult pending() {
            return new StartResult(true, null, null);
        }

        @NonNull
        public static StartResult done(@Nullable Object payload) {
            return new StartResult(false, payload, null);
        }

        @NonNull
        public static StartResult failed(@NonNull BleFailure failure) {
            return new StartResult(false, null, failure);
        }

        public boolean isPending() {
            return pending;
        }
    }

    public interface TaskExecutor {
        @NonNull
        StartResult start();
    }

    public interface CompletionHandler {
        default void onSuccess(@Nullable Object payload) {
        }

        void onCancelled();

        void onFailed(@NonNull BleFailure failure);
    }

    private final class Op {
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
        final boolean transactional;
        @NonNull
        final TaskExecutor executor;
        @NonNull
        final CompletionHandler handler;
        boolean zombie;
        boolean notified;
        long startedAtMillis;
        @Nullable
        YoTimeoutHandle timer;

        Op(long sequenceId,
           @NonNull String operationId,
           @NonNull OpKind kind,
           @Nullable BleGattTarget target,
           @NonNull BleOperationPriority priority,
           long timeoutMillis,
           boolean transactional,
           @NonNull TaskExecutor executor,
           @NonNull CompletionHandler handler) {
            this.sequenceId = sequenceId;
            this.operationId = operationId;
            this.kind = kind;
            this.target = target;
            this.priority = priority;
            this.timeoutMillis = timeoutMillis;
            this.transactional = transactional;
            this.executor = executor;
            this.handler = handler;
        }
    }

    @NonNull
    private final BleDeviceId deviceId;
    @NonNull
    private final YoBleRuntime runtime;
    private final int capacity;
    private final PriorityQueue<Op> pending = new PriorityQueue<>(Comparator
            .comparingInt((Op op) -> op.priority.rank())
            .thenComparingLong(op -> op.sequenceId));
    private long sequence;
    @Nullable
    private Op inFlight;
    private boolean draining;
    private boolean drainPosted;
    private volatile int publishedPendingCount;

    public YoBleGattOperationQueue(@NonNull BleDeviceId deviceId,
                                   @NonNull YoBleRuntime runtime,
                                   int capacity) {
        this.deviceId = deviceId;
        this.runtime = runtime;
        this.capacity = Math.max(4, capacity);
    }

    /** 用户仍在等待结果的操作数（排队中的加上尚未通知的在途操作）。可跨线程读。 */
    public int publishedPendingCount() {
        return publishedPendingCount;
    }

    public void enqueue(@NonNull String operationId,
                        @NonNull OpKind kind,
                        @Nullable BleGattTarget target,
                        @NonNull BleOperationPriority priority,
                        long timeoutMillis,
                        boolean transactional,
                        @NonNull TaskExecutor executor,
                        @NonNull CompletionHandler handler) {
        runtime.checkStateThread();
        int occupied = pending.size() + (inFlight != null ? 1 : 0);
        if (occupied >= capacity) {
            BleFailure full = YoBleErrorMapper.failure(
                    BleErrorCode.GATT_QUEUE_FULL, "ble-queue",
                    "GATT operation queue is full", deviceId);
            runtime.state(() -> handler.onFailed(full));
            return;
        }
        Op op = new Op(++sequence, operationId, kind, target, priority,
                timeoutMillis, transactional, executor, handler);
        pending.add(op);
        publish();
        requestDrain();
    }

    public void enqueue(@NonNull String operationId,
                        @NonNull OpKind kind,
                        @Nullable BleGattTarget target,
                        @NonNull BleOperationPriority priority,
                        long timeoutMillis,
                        @NonNull TaskExecutor executor,
                        @NonNull CompletionHandler handler) {
        enqueue(operationId, kind, target, priority, timeoutMillis, false, executor, handler);
    }

    /** 取消一条尚未通知结果的操作。在途操作变为僵尸槽，不立刻放行下一条。 */
    public void cancel(@NonNull String operationId) {
        runtime.checkStateThread();
        Iterator<Op> it = pending.iterator();
        while (it.hasNext()) {
            Op op = it.next();
            if (op.operationId.equals(operationId)) {
                it.remove();
                publish();
                notifyCancelled(op);
                return;
            }
        }
        if (inFlight != null && inFlight.operationId.equals(operationId) && !inFlight.notified) {
            becomeZombie(inFlight, true);
        }
    }

    /** 取消仍在排队、且标记为可靠写事务的写操作。 */
    public int cancelPendingTransactional() {
        runtime.checkStateThread();
        int count = 0;
        Iterator<Op> it = pending.iterator();
        while (it.hasNext()) {
            Op op = it.next();
            if (!op.transactional) {
                continue;
            }
            it.remove();
            count++;
            notifyCancelled(op);
        }
        publish();
        return count;
    }

    /**
     * 用平台回调完成在途操作。目标不匹配时忽略并返回 false。
     * 僵尸槽被匹配回调静默释放。
     */
    public boolean complete(@NonNull OpKind kind,
                            @Nullable BleGattTarget target,
                            @Nullable Object payload,
                            @Nullable BleFailure failure) {
        runtime.checkStateThread();
        Op op = inFlight;
        if (op == null || op.kind != kind || !sameTarget(op.target, target)) {
            if (op != null) {
                YoBtLogger.d(TAG, "忽略不匹配的 GATT 回调 kind=" + kind + " target=" + target);
            }
            return false;
        }
        cancelTimer(op);
        inFlight = null;
        if (!op.zombie && !op.notified) {
            op.notified = true;
            if (failure != null) {
                op.handler.onFailed(failure);
            } else {
                op.handler.onSuccess(payload);
            }
        }
        publish();
        requestDrain();
        return true;
    }

    /** 只清排队中的操作，不动在途槽。返回被清掉的数量。 */
    public int clearPending(@NonNull BleFailure reason) {
        runtime.checkStateThread();
        int count = pending.size();
        while (!pending.isEmpty()) {
            Op op = pending.poll();
            if (op != null) {
                op.handler.onFailed(reason);
            }
        }
        publish();
        return count;
    }

    /** 拆链时使用：在途与排队全部以同一原因结束，并放行槽位。 */
    public void clearAll(@NonNull BleFailure reason) {
        runtime.checkStateThread();
        Op current = inFlight;
        inFlight = null;
        if (current != null) {
            cancelTimer(current);
            if (!current.notified) {
                current.notified = true;
                current.handler.onFailed(reason);
            }
        }
        clearPending(reason);
    }

    private void requestDrain() {
        if (draining || inFlight != null || pending.isEmpty() || drainPosted) {
            return;
        }
        drainPosted = true;
        runtime.state(this::drain);
    }

    private void drain() {
        runtime.checkStateThread();
        drainPosted = false;
        if (inFlight != null) {
            return;
        }
        draining = true;
        try {
            while (inFlight == null && !pending.isEmpty()) {
                Op op = pending.poll();
                if (op == null) {
                    break;
                }
                inFlight = op;
                op.startedAtMillis = System.currentTimeMillis();
                publish();
                StartResult result;
                try {
                    result = op.executor.start();
                } catch (Throwable t) {
                    result = StartResult.failed(YoBleErrorMapper.failure(
                            BleErrorCode.INTERNAL_ERROR, "ble-queue",
                            "GATT operation start threw", t,
                            BleFailure.NO_RAW_STATUS, deviceId, op.target, null));
                }
                if (inFlight != op) {
                    continue;
                }
                if (result.isPending()) {
                    armTimeout(op);
                    break;
                }
                inFlight = null;
                if (!op.notified) {
                    op.notified = true;
                    if (result.failure != null) {
                        op.handler.onFailed(result.failure);
                    } else {
                        op.handler.onSuccess(result.payload);
                    }
                }
                publish();
            }
        } finally {
            draining = false;
        }
        if (inFlight == null && !pending.isEmpty()) {
            requestDrain();
        }
    }

    private void armTimeout(@NonNull Op op) {
        if (op.timeoutMillis <= 0L) {
            return;
        }
        op.timer = runtime.stateDelayed(() -> onTimeout(op), op.timeoutMillis);
    }

    private void onTimeout(@NonNull Op op) {
        if (inFlight != op || op.zombie || op.notified) {
            return;
        }
        BleFailure timeout = YoBleErrorMapper.failure(
                BleErrorCode.GATT_OPERATION_TIMEOUT, "ble-queue",
                "GATT operation timed out", deviceId);
        becomeZombie(op, false);
        op.handler.onFailed(timeout);
    }

    private void becomeZombie(@NonNull Op op, boolean notifyCancel) {
        op.zombie = true;
        if (notifyCancel && !op.notified) {
            op.notified = true;
            op.handler.onCancelled();
        } else if (!notifyCancel) {
            op.notified = true;
        }
        cancelTimer(op);
        long deadline = op.startedAtMillis
                + Math.max(0L, op.timeoutMillis)
                + STALE_CALLBACK_GRACE_MS;
        long wait = Math.max(0L, deadline - System.currentTimeMillis());
        op.timer = runtime.stateDelayed(() -> releaseZombie(op), wait);
        publish();
    }

    private void releaseZombie(@NonNull Op op) {
        if (inFlight != op) {
            return;
        }
        inFlight = null;
        publish();
        requestDrain();
    }

    private void notifyCancelled(@NonNull Op op) {
        if (op.notified) {
            return;
        }
        op.notified = true;
        op.handler.onCancelled();
    }

    private void cancelTimer(@NonNull Op op) {
        if (op.timer != null) {
            op.timer.cancel();
            op.timer = null;
        }
    }

    private void publish() {
        int waiting = pending.size();
        if (inFlight != null && !inFlight.zombie) {
            waiting++;
        }
        publishedPendingCount = waiting;
    }

    private static boolean sameTarget(@Nullable BleGattTarget queued, @Nullable BleGattTarget incoming) {
        if (queued == null || incoming == null) {
            return queued == null && incoming == null;
        }
        return queued.matches(incoming);
    }
}
