package com.yovo.lib_ai.internal.runtime;

import java.util.ArrayDeque;
import java.util.concurrent.Executor;

/**
 * 把监听器回调按提交顺序排到指定执行器上。执行器是多线程时仍然保持顺序。
 */
public final class OrderedCallbacks {

    private final Executor executor;
    private final Object lock = new Object();
    private final ArrayDeque<Runnable> queue = new ArrayDeque<>();
    private boolean draining;

    public OrderedCallbacks(Executor executor) {
        this.executor = executor;
    }

    public void submit(Runnable task) {
        synchronized (lock) {
            queue.add(task);
            if (draining) {
                return;
            }
            draining = true;
        }
        executor.execute(this::drain);
    }

    private void drain() {
        while (true) {
            Runnable task;
            synchronized (lock) {
                task = queue.poll();
                if (task == null) {
                    draining = false;
                    if (!queue.isEmpty()) {
                        draining = true;
                        continue;
                    }
                    return;
                }
            }
            try {
                task.run();
            } catch (RuntimeException ignored) {
                // 队列必须继续把后面的回调交出去，否则一条通道的监听器会卡住其他通道。
            }
        }
    }
}
