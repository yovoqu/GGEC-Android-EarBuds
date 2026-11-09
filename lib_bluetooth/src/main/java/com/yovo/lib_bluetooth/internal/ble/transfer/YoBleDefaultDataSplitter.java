package com.yovo.lib_bluetooth.internal.ble.transfer;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.model.ble.BleDataSplitter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 默认 MTU 定长分片器（对齐 Nordic DefaultMtuSplitter / Polidea 分片逻辑）。
 */
public final class YoBleDefaultDataSplitter implements BleDataSplitter {

    public static final YoBleDefaultDataSplitter INSTANCE = new YoBleDefaultDataSplitter();

    @NonNull
    @Override
    public List<byte[]> split(@NonNull byte[] payload, int maxChunkSize) {
        int chunkLimit = Math.max(1, maxChunkSize);
        if (payload.length == 0) {
            List<byte[]> singleEmpty = new ArrayList<>(1);
            singleEmpty.add(new byte[0]);
            return singleEmpty;
        }
        int totalChunks = (payload.length + chunkLimit - 1) / chunkLimit;
        List<byte[]> chunks = new ArrayList<>(totalChunks);
        int offset = 0;
        while (offset < payload.length) {
            int end = Math.min(payload.length, offset + chunkLimit);
            chunks.add(Arrays.copyOfRange(payload, offset, end));
            offset = end;
        }
        return chunks;
    }
}
