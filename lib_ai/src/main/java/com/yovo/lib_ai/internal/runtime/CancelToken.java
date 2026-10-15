package com.yovo.lib_ai.internal.runtime;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 调用方取消和客户端关闭共用的开关。传输层把实际的 OkHttp cancel 挂到这里。
 */
public final class CancelToken {

    private final AtomicBoolean cancelled = new AtomicBoolean();
    private volatile Runnable action = () -> {
    };

    public void cancel() {
        cancelled.set(true);
        action.run();
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public void onCancel(Runnable action) {
        this.action = action == null ? () -> {
        } : action;
        if (cancelled.get()) {
            this.action.run();
        }
    }
}
