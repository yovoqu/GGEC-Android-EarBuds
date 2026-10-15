package com.yovo.lib_bluetooth.internal.classic.protocol;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_bluetooth.internal.classic.protocol.packet.YoCbtPacketFramer;

import org.junit.Test;

import java.util.List;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description {@link YoCbtPacketFramer} 粘包拆包帧解析的单元测试。
 */

public class YoCbtPacketFramerTest {

    @Test
    public void singleFrame() {
        YoCbtPacketFramer framer = new YoCbtPacketFramer();
        byte[] payload = "hello".getBytes();
        byte[] framed = YoCbtPacketFramer.frame(payload);
        List<byte[]> frames = framer.feed(framed);
        assertEquals(1, frames.size());
        assertArrayEquals(payload, frames.get(0));
    }

    @Test
    public void stickyPackets() {
        YoCbtPacketFramer framer = new YoCbtPacketFramer();
        byte[] p1 = "first".getBytes();
        byte[] p2 = "second".getBytes();
        byte[] f1 = YoCbtPacketFramer.frame(p1);
        byte[] f2 = YoCbtPacketFramer.frame(p2);
        byte[] combined = new byte[f1.length + f2.length];
        System.arraycopy(f1, 0, combined, 0, f1.length);
        System.arraycopy(f2, 0, combined, f1.length, f2.length);
        List<byte[]> frames = framer.feed(combined);
        assertEquals(2, frames.size());
        assertArrayEquals(p1, frames.get(0));
        assertArrayEquals(p2, frames.get(1));
    }

    @Test
    public void splitPacket() {
        YoCbtPacketFramer framer = new YoCbtPacketFramer();
        byte[] payload = "split-test-data".getBytes();
        byte[] framed = YoCbtPacketFramer.frame(payload);

        // 仅输入头部 + 部分载荷
        int split = 3;
        byte[] part1 = new byte[split];
        System.arraycopy(framed, 0, part1, 0, split);
        List<byte[]> result1 = framer.feed(part1);
        assertTrue(result1.isEmpty());

        // 输入剩余部分
        byte[] part2 = new byte[framed.length - split];
        System.arraycopy(framed, split, part2, 0, part2.length);
        List<byte[]> result2 = framer.feed(part2);
        assertEquals(1, result2.size());
        assertArrayEquals(payload, result2.get(0));
    }

    @Test
    public void multipleStickyAndSplit() {
        YoCbtPacketFramer framer = new YoCbtPacketFramer();
        byte[] p1 = "alpha".getBytes();
        byte[] p2 = "beta".getBytes();
        byte[] f1 = YoCbtPacketFramer.frame(p1);
        byte[] f2 = YoCbtPacketFramer.frame(p2);
        byte[] combined = new byte[f1.length + f2.length];
        System.arraycopy(f1, 0, combined, 0, f1.length);
        System.arraycopy(f2, 0, combined, f1.length, f2.length);

        // 在第二帧中间拆分
        int split = f1.length + 2;
        byte[] part1 = new byte[split];
        System.arraycopy(combined, 0, part1, 0, split);
        List<byte[]> r1 = framer.feed(part1);
        assertEquals(1, r1.size());
        assertArrayEquals(p1, r1.get(0));

        byte[] part2 = new byte[combined.length - split];
        System.arraycopy(combined, split, part2, 0, part2.length);
        List<byte[]> r2 = framer.feed(part2);
        assertEquals(1, r2.size());
        assertArrayEquals(p2, r2.get(0));
    }

    @Test
    public void emptyPayload() {
        YoCbtPacketFramer framer = new YoCbtPacketFramer();
        byte[] framed = YoCbtPacketFramer.frame(new byte[0]);
        List<byte[]> frames = framer.feed(framed);
        assertEquals(1, frames.size());
        assertEquals(0, frames.get(0).length);
    }

    @Test
    public void reset() {
        YoCbtPacketFramer framer = new YoCbtPacketFramer();
        byte[] framed = YoCbtPacketFramer.frame("data".getBytes());
        // 仅输入头部
        framer.feed(new byte[]{framed[0]});
        assertEquals(1, framer.bufferedBytes());
        framer.reset();
        assertEquals(0, framer.bufferedBytes());
    }

    @Test
    public void largePayload() {
        YoCbtPacketFramer framer = new YoCbtPacketFramer();
        byte[] payload = new byte[4096];
        for (int i = 0; i < payload.length; i++) payload[i] = (byte) (i % 256);
        byte[] framed = YoCbtPacketFramer.frame(payload);
        List<byte[]> frames = framer.feed(framed);
        assertEquals(1, frames.size());
        assertArrayEquals(payload, frames.get(0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void frameExceedsMaxSize() {
        YoCbtPacketFramer.frame(new byte[65536]);
    }
}
