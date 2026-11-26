package com.yovo.biz_ggec_wy.internal.link;

import com.yovo.biz_ggec_wy.api.WyDevice;

import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WyBatteryProtocolTest {

    @Test
    public void queryFramesMatchSpec() {
        assertArrayEquals(
                new byte[]{(byte) 0x99, (byte) 0xEC, (byte) 0x80, 0x00, 0x01, 0x00, 0x12, 0x34},
                WyBatteryProtocol.queryFrame(WyBatteryProtocol.PART_ALL));
        assertArrayEquals(
                new byte[]{(byte) 0x99, (byte) 0xEC, (byte) 0x80, 0x00, 0x01, 0x01, 0x12, 0x34},
                WyBatteryProtocol.queryFrame(WyBatteryProtocol.PART_LEFT));
        assertArrayEquals(
                new byte[]{(byte) 0x99, (byte) 0xEC, (byte) 0x80, 0x00, 0x01, 0x02, 0x12, 0x34},
                WyBatteryProtocol.queryFrame(WyBatteryProtocol.PART_RIGHT));
        assertArrayEquals(
                new byte[]{(byte) 0x99, (byte) 0xEC, (byte) 0x80, 0x00, 0x01, 0x03, 0x12, 0x34},
                WyBatteryProtocol.queryFrame(WyBatteryProtocol.PART_CASE));
    }

    @Test
    public void parseThreePartBatteryFrame() {
        // 99 EC 80 00 04 00 50 5A 64 12 34 -> left=80, right=90, case=100
        byte[] frame = new byte[]{
                (byte) 0x99, (byte) 0xEC, (byte) 0x80, 0x00, 0x04, 0x00,
                0x50, 0x5A, 0x64, 0x12, 0x34
        };
        Optional<WyBatteryProtocol.BatteryUpdate> parsed = WyBatteryProtocol.parseFrame(frame);
        assertTrue(parsed.isPresent());
        assertEquals(new WyBatteryProtocol.BatteryUpdate.All(80, 90, 100), parsed.get());
    }

    @Test
    public void parseThreePartBatteryFrameWithUnknownParts() {
        // 0x00 和 0xFF 均归为 UNKNOWN_LEVEL (-1)
        byte[] frame = new byte[]{
                (byte) 0x99, (byte) 0xEC, (byte) 0x80, 0x00, 0x04, 0x00,
                0x00, 0x4B, (byte) 0xFF, 0x12, 0x34
        };
        Optional<WyBatteryProtocol.BatteryUpdate> parsed = WyBatteryProtocol.parseFrame(frame);
        assertTrue(parsed.isPresent());
        assertEquals(
                new WyBatteryProtocol.BatteryUpdate.All(
                        WyDevice.Battery.UNKNOWN_LEVEL, 75, WyDevice.Battery.UNKNOWN_LEVEL),
                parsed.get());
    }

    @Test
    public void parseSinglePartBatteryFrames() {
        byte[] leftFrame = new byte[]{
                (byte) 0x99, (byte) 0xEC, (byte) 0x80, 0x00, 0x02, 0x01, 0x32, 0x12, 0x34
        };
        byte[] rightFrame = new byte[]{
                (byte) 0x99, (byte) 0xEC, (byte) 0x80, 0x00, 0x02, 0x02, 0x46, 0x12, 0x34
        };
        byte[] caseFrame = new byte[]{
                (byte) 0x99, (byte) 0xEC, (byte) 0x80, 0x00, 0x02, 0x03, 0x64, 0x12, 0x34
        };

        assertEquals(Optional.of(new WyBatteryProtocol.BatteryUpdate.Single(1, 50)),
                WyBatteryProtocol.parseFrame(leftFrame));
        assertEquals(Optional.of(new WyBatteryProtocol.BatteryUpdate.Single(2, 70)),
                WyBatteryProtocol.parseFrame(rightFrame));
        assertEquals(Optional.of(new WyBatteryProtocol.BatteryUpdate.Single(3, 100)),
                WyBatteryProtocol.parseFrame(caseFrame));
    }

    @Test
    public void accumulatorHandlesSplitAndCoalescedFrames() {
        WyBatteryProtocol.FrameAccumulator acc = new WyBatteryProtocol.FrameAccumulator();

        byte[] part1 = new byte[]{(byte) 0x99, (byte) 0xEC, (byte) 0x80, 0x00, 0x04};
        byte[] part2 = new byte[]{
                0x00, 0x28, 0x32, 0x3C, 0x12, 0x34,
                (byte) 0x99, (byte) 0xEC, (byte) 0x80, 0x00, 0x02, 0x01, 0x55, 0x12, 0x34
        };

        assertTrue(acc.append(part1).isEmpty());
        List<byte[]> frames = acc.append(part2);
        assertEquals(2, frames.size());
        assertEquals(
                Optional.of(new WyBatteryProtocol.BatteryUpdate.All(40, 50, 60)),
                WyBatteryProtocol.parseFrame(frames.get(0)));
        assertEquals(
                Optional.of(new WyBatteryProtocol.BatteryUpdate.Single(1, 85)),
                WyBatteryProtocol.parseFrame(frames.get(1)));
    }
}
