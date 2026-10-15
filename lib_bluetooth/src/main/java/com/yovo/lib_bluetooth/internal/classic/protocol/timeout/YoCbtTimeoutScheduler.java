package com.yovo.lib_bluetooth.internal.classic.protocol.timeout;

import androidx.annotation.NonNull;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 命令超时调度器，基于 ScheduledExecutorService 管理并批量取消超时任务。
 */
public final class YoCbtTimeoutScheduler {

    private final ScheduledExecutorService executor;
    private final CopyOnWriteArrayList<ScheduledFuture<?>> pendingFutures;

    /**
     * 创建由给定执行器支持的新调度器。
     *
     * @param executor 用于超时调度的 ScheduledExecutorService
     */
    public YoCbtTimeoutScheduler(@NonNull ScheduledExecutorService executor) {
        this.executor = executor;
        this.pendingFutures = new CopyOnWriteArrayList<>();
    }

    /**
     * 可用于取消已调度超时的句柄。
     */
    public interface Cancellable {
        /** 取消超时。幂等操作。 */
        void cancel();
    }

    /**
     * 在指定毫秒延迟后调度任务执行。
     *
     * @param task   超时触发时要执行的任务
     * @param millis 延迟时间（毫秒）
     * @return 用于在超时触发前取消的 {@link Cancellable} 句柄
     */
    @NonNull
    public Cancellable schedule(@NonNull Runnable task, long millis) {
        ScheduledFuture<?> future = executor.schedule(() -> {
            pendingFutures.removeIf(f -> f.isDone());
            task.run();
        }, millis, TimeUnit.MILLISECONDS);

        pendingFutures.add(future);

        return () -> {
            pendingFutures.remove(future);
            future.cancel(false);
        };
    }

    /**
     * 取消所有待处理的超时。已执行或已取消的 future 不受影响。
     */
    public void cancelAll() {
        for (ScheduledFuture<?> future : pendingFutures) {
            future.cancel(false);
        }
        pendingFutures.clear();
    }

    /**
     * 返回待处理（尚未执行或取消）的超时数量。
     */
    public int pendingCount() {
        pendingFutures.removeIf(ScheduledFuture::isDone);
        return pendingFutures.size();
    }
}
