package com.yovo.lib_bluetooth.internal.classic.protocol.packet;

import androidx.annotation.NonNull;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 协议层数据包值对象，封装原始载荷字节。
 */

public final class YoCbtPacket {
    private final byte[] payload;

    public YoCbtPacket(@NonNull byte[] payload) {
        this.payload = payload.clone();
    }

    @NonNull
    public byte[] getPayload() {
        return payload.clone();
    }
}
