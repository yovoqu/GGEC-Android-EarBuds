package com.yovo.biz_ggec_hs.internal.link;

/**
 * 主线程 + 定时器的可控替身：{@code post} 就地执行（保持投递顺序），
 * {@code postDelayed} 记账成「一次待跑的超时」，由用例显式推进。
 */
final class FakeMainThread implements HsSessionImpl.MainThread {

    private final Object lock = new Object();
    private Runnable pending;
    private Runnable lastPosted;
    private long delayMillis = -1L;

    @Override
    public void post(Runnable task) {
        task.run();
    }

    @Override
    public void postDelayed(Runnable task, long delayMillis) {
        synchronized (lock) {
            this.pending = task;
            this.lastPosted = task;
            this.delayMillis = delayMillis;
        }
    }

    @Override
    public void removeCallbacks(Runnable task) {
        synchronized (lock) {
            if (pending == task) {
                pending = null;
            }
        }
    }

    boolean isArmed() {
        synchronized (lock) {
            return pending != null;
        }
    }

    long delayMillis() {
        synchronized (lock) {
            return delayMillis;
        }
    }

    /** 最近一次排队的超时任务；撤表后仍保留引用，用来模拟「已取走消息」的迟到触发。 */
    Runnable lastScheduledTask() {
        synchronized (lock) {
            return lastPosted;
        }
    }

    /** 推进到超时时刻：跑掉已排队的超时任务；队列为空返回 false。 */
    boolean advanceToTimeout() {
        Runnable task;
        synchronized (lock) {
            task = pending;
            pending = null;
        }
        if (task == null) {
            return false;
        }
        task.run();
        return true;
    }
}
