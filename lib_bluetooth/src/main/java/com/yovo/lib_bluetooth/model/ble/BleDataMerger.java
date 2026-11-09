package com.yovo.lib_bluetooth.model.ble;

import androidx.annotation.NonNull;

/**
 * 流式 Notify/Indication 响应合包策略（对标 Nordic DataMerger）。
 */
public interface BleDataMerger {

    /**
     * 处理新到达的 Notify/Indicate 片段并返回合并判定。
     *
     * @param accumulatedBefore 已累计历史字节
     * @param newChunk          本次新到达的片段
     * @param chunkIndex        从 0 开始的片段序号
     * @return 合包状态与当前拼接字节
     */
    @NonNull
    Output merge(@NonNull byte[] accumulatedBefore, @NonNull byte[] newChunk, int chunkIndex);

    final class Output {
        @NonNull
        private final BleMergeResult status;
        @NonNull
        private final byte[] combined;

        public Output(@NonNull BleMergeResult status, @NonNull byte[] combined) {
            this.status = status;
            this.combined = combined.clone();
        }

        @NonNull
        public BleMergeResult status() {
            return status;
        }

        @NonNull
        public byte[] combined() {
            return combined.clone();
        }
    }
}
