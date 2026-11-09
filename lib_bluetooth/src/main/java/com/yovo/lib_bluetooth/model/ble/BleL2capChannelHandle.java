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

    void close();

    default void release() {
        close();
    }
}
