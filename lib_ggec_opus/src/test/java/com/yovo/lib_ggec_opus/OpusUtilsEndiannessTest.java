package com.yovo.lib_ggec_opus;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import com.bes.opus.OpusUtils;

import org.junit.Test;

/**
 * PCM 落盘端序必须稳定为小端：{@link OpusUtils#Shorts2Bytes(short[])} 固定走
 * {@code getBytes(s, false)}，不得依赖 {@code testCPU()} 的 CPU 端序探测。
 *
 * <p>16-bit PCM 约定：低字节在前（little-endian）。</p>
 */
public final class OpusUtilsEndiannessTest {

    @Test
    public void shorts2Bytes_is_little_endian() {
        // 0x1234 -> 低字节 0x34 在前，高字节 0x12 在后
        assertArrayEquals(new byte[] {0x34, 0x12},
                OpusUtils.Shorts2Bytes(new short[] {(short) 0x1234}));
    }

    @Test
    public void shorts2Bytes_handlesNegativeValues() {
        // -1 = 0xFFFF -> 小端 FF FF
        assertArrayEquals(new byte[] {(byte) 0xFF, (byte) 0xFF},
                OpusUtils.Shorts2Bytes(new short[] {(short) -1}));
        // -2 = 0xFFFE -> 小端 FE FF
        assertArrayEquals(new byte[] {(byte) 0xFE, (byte) 0xFF},
                OpusUtils.Shorts2Bytes(new short[] {(short) -2}));
    }

    @Test
    public void shorts2Bytes_preservesCountAndOrder() {
        byte[] out = OpusUtils.Shorts2Bytes(new short[] {0x0102, 0x0304});
        assertEquals("两样本 -> 四字节", 4, out.length);
        assertArrayEquals(new byte[] {0x02, 0x01, 0x04, 0x03}, out);
    }
}
