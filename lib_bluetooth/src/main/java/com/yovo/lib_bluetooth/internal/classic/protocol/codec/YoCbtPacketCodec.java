package com.yovo.lib_bluetooth.internal.classic.protocol.codec;

import com.yovo.lib_bluetooth.internal.classic.protocol.packet.YoCbtPacket;
import androidx.annotation.NonNull;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 数据包编解码组合接口，统一编码与解码操作。
 */

public interface YoCbtPacketCodec {
    @NonNull
    byte[] encode(@NonNull YoCbtPacket packet) throws Exception;

    @NonNull
    YoCbtPacket decode(@NonNull byte[] bytes) throws Exception;
}
