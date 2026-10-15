package com.yovo.lib_bluetooth.internal.classic.protocol.codec;

import com.yovo.lib_bluetooth.internal.classic.protocol.packet.YoCbtPacket;
import androidx.annotation.NonNull;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 原始载荷编解码器，不进行长度前缀帧封装。
 */

public final class YoCbtRawPacketCodec implements YoCbtPacketCodec {
    @NonNull
    @Override
    public byte[] encode(@NonNull YoCbtPacket packet) {
        return packet.getPayload();
    }

    @NonNull
    @Override
    public YoCbtPacket decode(@NonNull byte[] bytes) {
        return new YoCbtPacket(bytes);
    }
}
