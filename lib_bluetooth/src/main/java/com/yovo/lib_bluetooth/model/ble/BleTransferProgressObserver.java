package com.yovo.lib_bluetooth.model.ble;

import androidx.annotation.NonNull;

/**
 * 长包分片写入进度与结果观察者（适用 OTA / 录音大数据传输）。
 */
public interface BleTransferProgressObserver {

    default void onChunkSent(@NonNull BleDeviceId deviceId,
                             @NonNull BleGattTarget target,
                             int chunkIndex,
                             int totalChunks,
                             long bytesSent,
                             long totalBytes) {
    }

    default void onCompleted(@NonNull BleDeviceId deviceId,
                             @NonNull BleGattTarget target,
                             long totalBytes) {
    }

    default void onFailed(@NonNull BleFailure failure) {
    }
}
