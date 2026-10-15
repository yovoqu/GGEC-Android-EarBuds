package com.yovo.lib_bluetooth.internal.classic.protocol.codec;

import com.yovo.lib_bluetooth.internal.classic.protocol.packet.YoCbtPacket;
import com.yovo.lib_bluetooth.internal.classic.protocol.packet.YoCbtPacketFramer;
import androidx.annotation.NonNull;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 长度前缀帧格式编解码器，载荷编解码委托内部编解码器。
 */
public final class YoCbtFramedPacketCodec implements YoCbtPacketCodec {

    private final YoCbtPacketCodec innerCodec;
    private final YoCbtPacketFramer framer;

    public YoCbtFramedPacketCodec(@NonNull YoCbtPacketCodec innerCodec) {
        this.innerCodec = innerCodec;
        this.framer = new YoCbtPacketFramer();
    }

    @NonNull
    @Override
    public byte[] encode(@NonNull YoCbtPacket packet) throws Exception {
        byte[] payload = innerCodec.encode(packet);
        return YoCbtPacketFramer.frame(payload);
    }

    @NonNull
    @Override
    public YoCbtPacket decode(@NonNull byte[] bytes) throws Exception {
        return innerCodec.decode(bytes);
    }

    /**
     * 将原始传输字节送入帧解析器并解码所有完整数据包。
     * 透明处理粘包/拆包。
     *
     * @param rawBytes 从传输层收到的字节（可能含部分或多帧）
     * @param consumer 每解码出一个完整数据包时调用
     */
    public void feedAndDecode(@NonNull byte[] rawBytes,
                              @NonNull java.util.function.Consumer<YoCbtPacket> consumer) {
        for (byte[] frame : framer.feed(rawBytes)) {
            try {
                consumer.accept(decode(frame));
            } catch (Throwable ignored) {
            }
        }
    }

    public void resetFramer() {
        framer.reset();
    }

    public int bufferedBytes() {
        return framer.bufferedBytes();
    }

    @NonNull
    public YoCbtPacketCodec getInnerCodec() {
        return innerCodec;
    }
}
