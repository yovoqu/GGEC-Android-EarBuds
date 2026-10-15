package com.yovo.lib_bluetooth.internal.common.testing;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.common.concurrency.YoDispatcher;

import java.util.LinkedList;
import java.util.Queue;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 测试用 Fake {@link YoDispatcher}，记录任务并支持同步 flush。
 */
public final class FakeDispatcher implements YoDispatcher {
    private final Queue<Runnable> pendingTasks;

    public FakeDispatcher() {
        this.pendingTasks = new LinkedList<>();
    }

    @Override
    public synchronized void dispatch(@NonNull Runnable task) {
        pendingTasks.add(task);
    }

    /**
     * 按 FIFO 顺序同步执行所有待处理任务。
     */
    public synchronized void flush() {
        Runnable task;
        while ((task = pendingTasks.poll()) != null) {
            task.run();
        }
    }

    /**
     * 返回已 dispatch 但尚未 flush 的任务数量。
     */
    public synchronized int pendingCount() {
        return pendingTasks.size();
    }

    /**
     * 若无待处理任务则返回 true。
     */
    public synchronized boolean isIdle() {
        return pendingTasks.isEmpty();
    }

    /**
     * 清除所有待处理任务，不执行它们。
     */
    public synchronized void clear() {
        pendingTasks.clear();
    }
}
