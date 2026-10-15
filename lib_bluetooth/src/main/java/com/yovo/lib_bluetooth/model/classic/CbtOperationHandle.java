package com.yovo.lib_bluetooth.model.classic;

import androidx.annotation.NonNull;

/**
 * 经典蓝牙异步操作句柄。
 */
public final class CbtOperationHandle {

    @NonNull
    private final String id;
    @NonNull
    private final Runnable cancelAction;
    private boolean cancelled;

    public CbtOperationHandle(@NonNull String id, @NonNull Runnable cancelAction) {
        this.id = id;
        this.cancelAction = cancelAction;
    }

    @NonNull
    public String id() {
        return id;
    }

    public synchronized void cancel() {
        if (cancelled) {
            return;
        }
        cancelled = true;
        cancelAction.run();
    }
}
