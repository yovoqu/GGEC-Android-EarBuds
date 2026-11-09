package com.yovo.lib_bluetooth.internal.ble.testing;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.common.concurrency.YoTaskScheduler;
import com.yovo.lib_bluetooth.internal.common.concurrency.YoTimeoutHandle;
import com.yovo.lib_bluetooth.internal.common.testing.FakeClock;
import com.yovo.lib_bluetooth.internal.common.testing.FakeDispatcher;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于 {@link FakeClock} 的确定性虚拟定时调度器（零 Thread.sleep）。
 */
public final class FakeTaskScheduler implements YoTaskScheduler {

    private static final class ScheduledItem {
        final long triggerAtMillis;
        final long seq;
        final Runnable task;
        final AtomicBoolean cancelled = new AtomicBoolean(false);

        ScheduledItem(long triggerAtMillis, long seq, Runnable task) {
            this.triggerAtMillis = triggerAtMillis;
            this.seq = seq;
            this.task = task;
        }
    }

    private final FakeClock clock;
    private final AtomicLong seqGen = new AtomicLong();
    private final PriorityQueue<ScheduledItem> queue = new PriorityQueue<>(
            Comparator.<ScheduledItem>comparingLong(i -> i.triggerAtMillis)
                    .thenComparingLong(i -> i.seq));

    public FakeTaskScheduler(@NonNull FakeClock clock) {
        this.clock = clock;
    }

    @Override
    @NonNull
    public synchronized YoTimeoutHandle schedule(@NonNull Runnable task, long delayMillis) {
        long triggerAt = clock.nowMillis() + Math.max(0L, delayMillis);
        ScheduledItem item = new ScheduledItem(triggerAt, seqGen.incrementAndGet(), task);
        queue.add(item);
        return () -> item.cancelled.set(true);
    }

    /** 在当前虚拟时间下执行所有已到期任务。 */
    public void runDueTasks() {
        runDueUntil(clock.nowMillis());
    }

    /** 仅推进虚拟时间并依次触发区间内每批到期任务（不主动 flush Dispatcher）。 */
    public void advanceBy(long deltaMillis) {
        long target = clock.nowMillis() + Math.max(0L, deltaMillis);
        while (true) {
            ScheduledItem next = pollNextActiveBefore(target);
            if (next == null) {
                break;
            }
            if (next.triggerAtMillis > clock.nowMillis()) {
                clock.setMillis(next.triggerAtMillis);
            }
            next.task.run();
        }
        if (target > clock.nowMillis()) {
            clock.setMillis(target);
        }
    }

    /** 推进虚拟时间并在每次到期任务执行后自动 flush 所有 FakeDispatcher，精确驱动多级定时链。 */
    public void advanceAndFlush(long deltaMillis, @NonNull FakeDispatcher... dispatchers) {
        long target = clock.nowMillis() + Math.max(0L, deltaMillis);
        drain(dispatchers);
        while (true) {
            ScheduledItem next = pollNextActiveBefore(target);
            if (next == null) {
                break;
            }
            if (next.triggerAtMillis > clock.nowMillis()) {
                clock.setMillis(next.triggerAtMillis);
            }
            next.task.run();
            drain(dispatchers);
        }
        if (target > clock.nowMillis()) {
            clock.setMillis(target);
        }
        drain(dispatchers);
    }

    /** 在当前时间点反复 flush Dispatcher 与触发 0ms 到期定时任务，直到系统静态。 */
    public void drain(@NonNull FakeDispatcher... dispatchers) {
        boolean progressed;
        int guard = 0;
        do {
            progressed = false;
            for (FakeDispatcher d : dispatchers) {
                if (d.pendingCount() > 0) {
                    d.flush();
                    progressed = true;
                }
            }
            if (runDueUntil(clock.nowMillis()) > 0) {
                progressed = true;
            }
            guard++;
            if (guard > 10_000) {
                throw new IllegalStateException("FakeTaskScheduler.drain exceeded 10000 iterations");
            }
        } while (progressed);
    }

    public synchronized int pendingCount() {
        int count = 0;
        for (ScheduledItem item : queue) {
            if (!item.cancelled.get()) {
                count++;
            }
        }
        return count;
    }

    public synchronized void clear() {
        queue.clear();
    }

    private int runDueUntil(long maxTime) {
        int ran = 0;
        while (true) {
            ScheduledItem next = pollNextActiveBefore(maxTime);
            if (next == null) {
                break;
            }
            next.task.run();
            ran++;
        }
        return ran;
    }

    private synchronized ScheduledItem pollNextActiveBefore(long maxTime) {
        while (!queue.isEmpty()) {
            ScheduledItem head = queue.peek();
            if (head.cancelled.get()) {
                queue.poll();
                continue;
            }
            if (head.triggerAtMillis <= maxTime) {
                return queue.poll();
            }
            return null;
        }
        return null;
    }
}
