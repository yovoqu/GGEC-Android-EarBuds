package com.yovo.lib_bluetooth.model.ble;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.model.common.Result;

/**
 * L2CAP CoC 面向连接高速字节流通道句柄（API 29+）。
 */
public interface BleL2capChannelHandle {

    @NonNull
    String operationId();

    int psm();

    boolean isOpen();

    @NonNull
    Result<Void> write(@NonNull byte[] data);

    /**
     * 把写入交给该通道自己的发送线程，调用方不会停在套接字上。
     */
    @NonNull
    BleOperationHandle writeAsync(@NonNull byte[] data, @NonNull BleValueObserver<Void> observer);

    void close();

    default void release() {
        close();
    }
}
