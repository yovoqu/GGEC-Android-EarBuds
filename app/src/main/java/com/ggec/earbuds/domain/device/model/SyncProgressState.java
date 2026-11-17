package com.ggec.earbuds.domain.device.model;

import androidx.annotation.Nullable;

/**
 * 设备录音文件同步状态。
 *
 * <p>只承载结构化进度：哪个文件、是否在传、百分比与字节数。失败原因走
 * {@code RecordFailure}（{@code RecordEvent.SyncFailed}），不在进度里再放一份可空错误文案——
 * 技术英文串与用户句子都不属于领域层。</p>
 */
public record SyncProgressState(
        @Nullable String syncingFileId,
        boolean isSyncing,
        int percentage,
        long bytesReceived,
        long totalBytes
) {
    public static SyncProgressState idle() {
        return new SyncProgressState(null, false, 0, 0, 0);
    }
}
