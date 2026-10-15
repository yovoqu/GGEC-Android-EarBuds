package com.yovo.lib_bluetooth.internal.classic.protocol.codec;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.protocol.packet.YoCbtPacket;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 将原始字节解码为 {@link YoCbtPacket} 的解码器接口。
 */
@FunctionalInterface
public interface YoCbtPacketDecoder {

    /**
     * 将给定的原始字节解码为 {@link YoCbtPacket}。
     *
     * @param bytes 从传输层收到的原始字节
     * @return 解码后的数据包
     * @throws Exception 若解码因任何原因失败
     */
    @NonNull
    YoCbtPacket decode(@NonNull byte[] bytes) throws Exception;
}
