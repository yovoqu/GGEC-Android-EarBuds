package com.yovo.lib_bluetooth.internal.classic.protocol.packet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 字节流帧解析器，使用 2 字节大端长度前缀提取完整数据包帧。
 */
public final class YoCbtPacketFramer {

    private static final int HEADER_SIZE = 2;
    private static final int MAX_PAYLOAD_SIZE = 65535;

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private int expectedLength = -1;

    /**
     * 将收到的字节送入帧解析器。返回零个或多个完整帧。
     * 构成不完整帧的字节会在内部缓冲。
     *
     * @param data 从传输层收到的原始字节
     * @return 提取出的完整帧载荷列表
     */
    @NonNull
    public synchronized List<byte[]> feed(@NonNull byte[] data) {
        List<byte[]> frames = new ArrayList<>();
        if (data.length == 0) return frames;

        buffer.write(data, 0, data.length);

        while (true) {
            byte[] current = buffer.toByteArray();

            if (expectedLength < 0) {
                // 至少需要 2 字节作为帧头
                if (current.length < HEADER_SIZE) return frames;

                int hi = (current[0] & 0xFF) << 8;
                int lo = current[1] & 0xFF;
                expectedLength = hi | lo;

                if (expectedLength < 0 || expectedLength > MAX_PAYLOAD_SIZE) {
                    buffer.reset();
                    expectedLength = -1;
                    return frames;
                }
            }

            int totalFrameSize = HEADER_SIZE + expectedLength;
            if (current.length < totalFrameSize) return frames;

            // 提取一帧完整数据
            byte[] payload = Arrays.copyOfRange(current, HEADER_SIZE, totalFrameSize);
            frames.add(payload);

            // 从缓冲区移除已消费的字节
            buffer.reset();
            if (current.length > totalFrameSize) {
                buffer.write(current, totalFrameSize, current.length - totalFrameSize);
            }
            expectedLength = -1;
        }
    }

    /**
     * 将载荷编码为带 2 字节长度前缀的帧化字节数组。
     */
    @NonNull
    public static byte[] frame(@NonNull byte[] payload) {
        if (payload.length > MAX_PAYLOAD_SIZE) {
            throw new IllegalArgumentException("payload too large: " + payload.length);
        }
        byte[] framed = new byte[HEADER_SIZE + payload.length];
        framed[0] = (byte) ((payload.length >> 8) & 0xFF);
        framed[1] = (byte) (payload.length & 0xFF);
        System.arraycopy(payload, 0, framed, HEADER_SIZE, payload.length);
        return framed;
    }

    public synchronized void reset() {
        buffer.reset();
        expectedLength = -1;
    }

    public synchronized int bufferedBytes() {
        return buffer.size();
    }
}
