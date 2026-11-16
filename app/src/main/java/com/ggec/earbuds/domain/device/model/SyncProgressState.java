package com.ggec.earbuds.domain.device.model;

import androidx.annotation.Nullable;

/**
 * 设备录音文件同步状态。
 */
public record SyncProgressState(
        @Nullable String syncingFileId,
        boolean isSyncing,
        int percentage,
        long bytesReceived,
        long totalBytes,
        @Nullable String errorMessage
) {
    public static SyncProgressState idle() {
        return new SyncProgressState(null, false, 0, 0, 0, null);
    }
}
