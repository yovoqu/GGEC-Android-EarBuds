package com.yovo.biz_ggec_hs.internal.session;

import androidx.annotation.NonNull;

/**
 * 连接就绪看门狗：在给定延迟后、且期间未被取消时触发一次超时处理。
 * 只负责计时与取消；触发后的动作由调用方注入，便于用假调度器做纯 JVM 单测。
 */
final class ConnectReadyTimeout {

    /** 主线程投递的最小抽象，生产实现复用会话层已有的 Handler。 */
    interface Scheduler {
        void postDelayed(@NonNull Runnable task, long delayMillis);

        void removeCallbacks(@NonNull Runnable task);
    }

    private final Scheduler scheduler;
    private final long timeoutMillis;
    private final Runnable timeoutHandler;
    private final Runnable task = this::fire;
    private boolean armed;

    ConnectReadyTimeout(@NonNull Scheduler scheduler, long timeoutMillis, @NonNull Runnable timeoutHandler) {
        this.scheduler = scheduler;
        this.timeoutMillis = timeoutMillis;
        this.timeoutHandler = timeoutHandler;
    }

    void arm() {
        scheduler.removeCallbacks(task);
        synchronized (this) {
            armed = true;
        }
        scheduler.postDelayed(task, timeoutMillis);
    }

    void cancel() {
        synchronized (this) {
            armed = false;
        }
        scheduler.removeCallbacks(task);
    }

    private void fire() {
        synchronized (this) {
            if (!armed) {
                return;
            }
            armed = false;
        }
        timeoutHandler.run();
    }
}
