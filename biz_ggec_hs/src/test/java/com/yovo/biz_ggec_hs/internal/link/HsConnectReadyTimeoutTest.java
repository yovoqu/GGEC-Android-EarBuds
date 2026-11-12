package com.yovo.biz_ggec_hs.internal.link;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 就绪看门狗本体：按配置延迟起表、只触发一次、撤表后不再触发、迟到触发被丢弃。
 */
public final class HsConnectReadyTimeoutTest {

    @Test
    public void armsWithConfiguredDelayAndFiresOnce() {
        FakeScheduler scheduler = new FakeScheduler();
        AtomicInteger fired = new AtomicInteger();
        HsConnectReadyTimeout timeout =
                new HsConnectReadyTimeout(scheduler, 12_000L, fired::incrementAndGet);

        timeout.arm();

        assertEquals(12_000L, scheduler.delayMillis);
        assertNotNull(scheduler.task);
        assertTrue(scheduler.active);

        scheduler.task.run();
        assertEquals(1, fired.get());

        // 再次运行同一任务不得重复触发。
        scheduler.task.run();
        assertEquals(1, fired.get());
    }

    @Test
    public void cancelPreventsFiring() {
        FakeScheduler scheduler = new FakeScheduler();
        AtomicInteger fired = new AtomicInteger();
        HsConnectReadyTimeout timeout =
                new HsConnectReadyTimeout(scheduler, 12_000L, fired::incrementAndGet);

        timeout.arm();
        Runnable scheduled = scheduler.task;
        assertNotNull(scheduled);

        timeout.cancel();
        assertFalse(scheduler.active);

        scheduled.run();
        assertEquals(0, fired.get());
    }

    @Test
    public void rearmDoesNotDoubleFire() {
        FakeScheduler scheduler = new FakeScheduler();
        AtomicInteger fired = new AtomicInteger();
        HsConnectReadyTimeout timeout =
                new HsConnectReadyTimeout(scheduler, 12_000L, fired::incrementAndGet);

        timeout.arm();
        timeout.arm();

        scheduler.task.run();
        assertEquals(1, fired.get());

        scheduler.task.run();
        assertEquals(1, fired.get());
    }

    @Test
    public void nothingScheduledBeforeArm() {
        FakeScheduler scheduler = new FakeScheduler();
        new HsConnectReadyTimeout(scheduler, 12_000L, () -> {
        });
        assertNull(scheduler.task);
    }

    private static final class FakeScheduler implements HsConnectReadyTimeout.Scheduler {
        private Runnable task;
        private long delayMillis;
        private boolean active;

        @Override
        public void postDelayed(Runnable task, long delayMillis) {
            this.task = task;
            this.delayMillis = delayMillis;
            this.active = true;
        }

        @Override
        public void removeCallbacks(Runnable task) {
            if (this.task == task) {
                this.active = false;
            }
        }
    }
}
