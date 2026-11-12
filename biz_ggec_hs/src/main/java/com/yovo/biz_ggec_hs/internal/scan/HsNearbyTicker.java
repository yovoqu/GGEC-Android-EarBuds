package com.yovo.biz_ggec_hs.internal.scan;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * TTL 心跳：按固定周期触发一次淘汰检查。抽象出来是为了让注册表淘汰逻辑可以用假实现做纯单测。
 */
interface HsNearbyTicker {

    /** 重复调用以最后一次为准。 */
    void schedule(Runnable task, long periodMillis);

    /** 延迟一次。重复调用以最后一次为准，并取消周期任务。 */
    void scheduleOnce(Runnable task, long delayMillis);

    /** 停止已安排的任务。 */
    void cancel();
}

/** 生产实现：单线程守护执行器，每次 schedule 重建，避免残留线程。 */
final class HsNearbySystemTicker implements HsNearbyTicker {

    private ScheduledExecutorService executor;
    private ScheduledFuture<?> future;

    @Override
    public synchronized void schedule(Runnable task, long periodMillis) {
        cancel();
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "hs-nearby-ttl");
            thread.setDaemon(true);
            return thread;
        });
        future = executor.scheduleWithFixedDelay(
                task, periodMillis, periodMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public synchronized void scheduleOnce(Runnable task, long delayMillis) {
        cancel();
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "hs-nearby-retain");
            thread.setDaemon(true);
            return thread;
        });
        future = executor.schedule(task, delayMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public synchronized void cancel() {
        if (future != null) {
            future.cancel(false);
            future = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }
}
