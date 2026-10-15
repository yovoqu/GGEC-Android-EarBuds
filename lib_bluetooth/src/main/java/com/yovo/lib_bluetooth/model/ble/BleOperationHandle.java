package com.yovo.lib_bluetooth.model.ble;

import androidx.annotation.NonNull;

/**
 * BLE 异步操作句柄。
 */
public final class BleOperationHandle {

    @NonNull
    private final String operationId;
    @NonNull
    private final Runnable cancelAction;
    private boolean released;

    public BleOperationHandle(@NonNull String operationId, @NonNull Runnable cancelAction) {
        this.operationId = operationId;
        this.cancelAction = cancelAction;
    }

    @NonNull
    public String operationId() {
        return operationId;
    }

    public synchronized void release() {
        if (released) {
            return;
        }
        released = true;
        cancelAction.run();
    }
}
