package com.yovo.lib_bluetooth.internal.common.concurrency;

import androidx.annotation.NonNull;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 单线程串行执行器，保证任务按 FIFO 顺序且不并发执行。
 */
public final class YoSerialExecutor {

    private final ExecutorService executor;
    private final AtomicBoolean shutdown;

    public YoSerialExecutor(@NonNull String name) {
        this.executor = Executors.newSingleThreadExecutor(r -> new Thread(r, name));
        this.shutdown = new AtomicBoolean(false);
    }

    /**
     * 提交任务顺序执行。任务按 FIFO 顺序运行，且不会并发。
     */
    public void execute(@NonNull Runnable task) {
        if (shutdown.get()) {
            throw new IllegalStateException("YoSerialExecutor has been shut down");
        }
        executor.execute(task);
    }

    /**
     * 发起有序关闭。已提交的任务仍会执行，但不再接受新任务。
     */
    public void shutdown() {
        if (shutdown.compareAndSet(false, true)) {
            executor.shutdown();
        }
    }

    /**
     * 若执行器已关闭则返回 true。
     */
    public boolean isShutdown() {
        return shutdown.get();
    }
}
