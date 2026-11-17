package com.ggec.earbuds.testing.ai;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;

/** 先排队、由测试决定何时执行的线程池，用来演练"后台任务还没跑起来时用户又操作了一次"的时序。 */
public final class QueueExecutor implements Executor {

    private final Queue<Runnable> queue = new ArrayDeque<>();

    @Override
    public void execute(Runnable command) {
        queue.add(command);
    }

    public int pending() {
        return queue.size();
    }

    /** 只执行队首这一条，用来验证"事件被重新入队"而不是就地重入。 */
    public void runNext() {
        Runnable next = queue.poll();
        if (next != null) {
            next.run();
        }
    }

    /** 执行排队的全部任务，包括执行过程中新排进来的。 */
    public void drain() {
        Runnable next;
        while ((next = queue.poll()) != null) {
            next.run();
        }
    }
}
