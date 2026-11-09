package com.yovo.lib_bluetooth.model.ble;

import androidx.annotation.NonNull;

import java.util.List;

/**
 * 长数据包分片策略（对标 Nordic DataSplitter；可自定义分片以注入私有帧头/序号）。
 */
public interface BleDataSplitter {

    /**
     * 将原始字节流切分为若干片段，每段长度通常不超过 {@code maxChunkSize}。
     *
     * @param payload      完整待写载荷
     * @param maxChunkSize 当前连接允许的单包最大载荷字节数
     * @return 按顺序发送的分片列表
     */
    @NonNull
    List<byte[]> split(@NonNull byte[] payload, int maxChunkSize);
}
