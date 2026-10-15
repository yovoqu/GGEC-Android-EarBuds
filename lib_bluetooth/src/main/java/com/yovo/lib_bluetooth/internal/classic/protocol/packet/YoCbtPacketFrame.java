package com.yovo.lib_bluetooth.internal.classic.protocol.packet;

import androidx.annotation.NonNull;

import java.util.Arrays;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 带 2 字节大端长度前缀的帧化数据包。
 */
public final class YoCbtPacketFrame {

    private static final int HEADER_SIZE = 2;
    private static final int MAX_PAYLOAD = 65535;

    private final byte[] payload;

    private YoCbtPacketFrame(@NonNull byte[] payload) {
        this.payload = payload;
    }

    @NonNull
    public byte[] getPayload() {
        return Arrays.copyOf(payload, payload.length);
    }

    public int getLength() {
        return payload.length;
    }

    @NonNull
    public byte[] getFrameBytes() {
        byte[] frame = new byte[HEADER_SIZE + payload.length];
        frame[0] = (byte) ((payload.length >> 8) & 0xFF);
        frame[1] = (byte) (payload.length & 0xFF);
        System.arraycopy(payload, 0, frame, HEADER_SIZE, payload.length);
        return frame;
    }

    @NonNull
    public static YoCbtPacketFrame frame(@NonNull byte[] payload) {
        if (payload.length > MAX_PAYLOAD) {
            throw new IllegalArgumentException("payload exceeds max: " + payload.length);
        }
        return new YoCbtPacketFrame(payload);
    }
}
