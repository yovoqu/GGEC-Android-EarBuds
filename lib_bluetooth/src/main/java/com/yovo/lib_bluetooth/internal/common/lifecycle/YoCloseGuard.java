package com.yovo.lib_bluetooth.internal.common.lifecycle;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 释放状态守卫，保证 {@code release} 操作幂等且线程安全。
 */
public final class YoCloseGuard {
    private final AtomicBoolean released = new AtomicBoolean(false);

    public boolean markReleased() {
        return released.compareAndSet(false, true);
    }

    public boolean isReleased() {
        return released.get();
    }
}
