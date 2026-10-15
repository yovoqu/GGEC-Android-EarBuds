package com.yovo.lib_bluetooth.internal.classic.protocol.codec;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.protocol.packet.YoCbtPacket;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 将 {@link YoCbtPacket} 编码为可通过蓝牙传输的原始字节的编码器接口。
 */
@FunctionalInterface
public interface YoCbtPacketEncoder {

    /**
     * 将给定的数据包编码为可供传输的字节数组。
     *
     * @param packet 待编码的数据包
     * @return 编码后的字节数组
     * @throws Exception 若编码因任何原因失败
     */
    @NonNull
    byte[] encode(@NonNull YoCbtPacket packet) throws Exception;
}
