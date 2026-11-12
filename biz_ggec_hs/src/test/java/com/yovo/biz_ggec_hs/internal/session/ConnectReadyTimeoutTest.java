package com.yovo.biz_ggec_hs.internal.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

public final class ConnectReadyTimeoutTest {

    @Test
    public void armsWithConfiguredDelayAndFiresOnce() {
        FakeScheduler scheduler = new FakeScheduler();
        AtomicInteger fired = new AtomicInteger();
        ConnectReadyTimeout timeout = new ConnectReadyTimeout(scheduler, 12_000L, fired::incrementAndGet);

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
        ConnectReadyTimeout timeout = new ConnectReadyTimeout(scheduler, 12_000L, fired::incrementAndGet);

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
        ConnectReadyTimeout timeout = new ConnectReadyTimeout(scheduler, 12_000L, fired::incrementAndGet);

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
        ConnectReadyTimeout timeout = new ConnectReadyTimeout(scheduler, 12_000L, () -> {
        });
        assertNull(scheduler.task);
    }

    private static final class FakeScheduler implements ConnectReadyTimeout.Scheduler {
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
