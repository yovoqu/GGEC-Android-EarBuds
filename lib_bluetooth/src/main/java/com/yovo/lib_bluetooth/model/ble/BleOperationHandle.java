package com.yovo.lib_bluetooth.model.ble;

import androidx.annotation.NonNull;

/**
 * BLE 异步操作句柄，同时兼容 {@link #cancel()} 与 {@link #release()}，幂等执行取消动作。
 */
public final class BleOperationHandle {

    @NonNull
    private final String operationId;
    @NonNull
    private final Runnable cancelAction;
    private volatile boolean released;

    public BleOperationHandle(@NonNull String operationId, @NonNull Runnable cancelAction) {
        this.operationId = operationId;
        this.cancelAction = cancelAction;
    }

    @NonNull
    public static BleOperationHandle completed(@NonNull String operationId) {
        BleOperationHandle handle = new BleOperationHandle(operationId, () -> { });
        handle.released = true;
        return handle;
    }

    @NonNull
    public String operationId() {
        return operationId;
    }

    public boolean isCancelled() {
        return released;
    }

    public boolean isReleased() {
        return released;
    }

    public synchronized void cancel() {
        release();
    }

    public synchronized void release() {
        if (released) {
            return;
        }
        released = true;
        cancelAction.run();
    }
}
