package com.yovo.lib_bluetooth.internal.classic.protocol.packet;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 字节流数据包边界检测器，处理粘包与拆包（2 字节长度前缀帧）。
 */
public final class YoCbtPacketBoundaryDetector {

    private static final int HEADER_SIZE = 2;

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private int expectedLength = -1;

    @NonNull
    public synchronized List<byte[]> feed(@NonNull byte[] data) {
        if (data.length == 0) return Collections.emptyList();
        List<byte[]> complete = new ArrayList<>();
        buffer.write(data, 0, data.length);

        while (true) {
            byte[] current = buffer.toByteArray();
            if (expectedLength < 0) {
                if (current.length < HEADER_SIZE) return complete;
                expectedLength = ((current[0] & 0xFF) << 8) | (current[1] & 0xFF);
                if (expectedLength < 0 || expectedLength > 65535) {
                    buffer.reset();
                    expectedLength = -1;
                    return complete;
                }
            }
            int total = HEADER_SIZE + expectedLength;
            if (current.length < total) return complete;

            byte[] payload = new byte[expectedLength];
            System.arraycopy(current, HEADER_SIZE, payload, 0, expectedLength);
            complete.add(payload);

            buffer.reset();
            if (current.length > total) {
                buffer.write(current, total, current.length - total);
            }
            expectedLength = -1;
        }
    }

    public synchronized void reset() {
        buffer.reset();
        expectedLength = -1;
    }

    public synchronized int bufferedByteCount() {
        return buffer.size();
    }
}
