package com.yovo.lib_bluetooth.internal.common.testing;

import androidx.annotation.NonNull;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 测试用可控时钟，可手动设置与推进时间。
 */
public final class FakeClock {
    private final AtomicLong currentMillis;
    private final long startMillis;

    public FakeClock() {
        this(System.currentTimeMillis());
    }

    public FakeClock(long initialMillis) {
        this.currentMillis = new AtomicLong(initialMillis);
        this.startMillis = initialMillis;
    }

    /**
     * 将时钟设置为指定的绝对毫秒值。
     */
    public void setMillis(long millis) {
        currentMillis.set(millis);
    }

    /**
     * 将时钟向前推进指定的毫秒数。
     */
    public void advanceMillis(long delta) {
        currentMillis.addAndGet(delta);
    }

    /**
     * 返回当前假时钟时间（毫秒）。
     */
    public long nowMillis() {
        return currentMillis.get();
    }

    /**
     * 返回真实系统时钟时间（毫秒）。
     */
    public long realNowMillis() {
        return System.currentTimeMillis();
    }

    /**
     * 返回创建时钟时的初始时间（毫秒）。
     */
    public long startMillis() {
        return startMillis;
    }

    /**
     * 返回自构造以来经过的假时钟时间（毫秒）。
     */
    public long elapsedMillis() {
        return currentMillis.get() - startMillis;
    }

    @NonNull
    @Override
    public String toString() {
        return "FakeClock{nowMillis=" + currentMillis.get()
                + ", startMillis=" + startMillis + "}";
    }
}
