package com.yovo.lib_bluetooth.model.ble;

import androidx.annotation.NonNull;

/**
 * BLE 异步操作句柄，同时兼容 {@link #cancel()} 与 {@link #release()}，幂等执行取消动作。
 *
 * <p>可被扩展：订阅级句柄（带额外能力的取消句柄）继承本类后仍是同一个可取消句柄类型，
 * 调用方按 {@code BleOperationHandle} 收口即可，无需分支判断。</p>
 */
public class BleOperationHandle {

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
