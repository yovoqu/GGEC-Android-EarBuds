package com.yovo.lib_bluetooth.internal.classic.protocol;

import static org.junit.Assert.assertArrayEquals;

import com.yovo.lib_bluetooth.internal.classic.protocol.codec.YoCbtRawPacketCodec;
import com.yovo.lib_bluetooth.internal.classic.protocol.packet.YoCbtPacket;

import org.junit.Test;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description {@link YoCbtRawPacketCodec} 原始载荷编解码的单元测试。
 */

public class YoCbtRawPacketCodecTest {
    @Test
    public void rawCodecPreservesPayload() {
        YoCbtRawPacketCodec codec = new YoCbtRawPacketCodec();
        byte[] payload = new byte[]{0x01, 0x02, 0x03};

        byte[] encoded = codec.encode(new YoCbtPacket(payload));
        YoCbtPacket decoded = codec.decode(encoded);

        assertArrayEquals(payload, encoded);
        assertArrayEquals(payload, decoded.getPayload());
    }
}
