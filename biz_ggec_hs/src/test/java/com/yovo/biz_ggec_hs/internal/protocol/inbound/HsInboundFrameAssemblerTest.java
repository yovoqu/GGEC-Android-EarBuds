package com.yovo.biz_ggec_hs.internal.protocol.inbound;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class HsInboundFrameAssemblerTest {

    private static final String FRAME_A = "99EC98000201021234";          // 99EC98, len=2 bytes
    private static final String FRAME_B = "99EC890004010203041234";      // 99EC89, len=4 bytes
    private static final String COMBINED_BATTERY = "99EC8000030102030405061234"; // 固定 26 字符

    @Test
    public void completeFramePassesThroughUnchanged() {
        HsInboundFrameAssembler assembler = new HsInboundFrameAssembler();
        List<String> frames = assembler.accept(FRAME_A);
        assertEquals(List.of(FRAME_A), frames);
    }

    @Test
    public void fragmentedFrameIsReassembled() {
        HsInboundFrameAssembler assembler = new HsInboundFrameAssembler();
        List<String> collected = new ArrayList<>();
        for (int i = 0; i < FRAME_A.length(); i += 4) {
            int end = Math.min(FRAME_A.length(), i + 4);
            collected.addAll(assembler.accept(FRAME_A.substring(i, end)));
        }
        assertEquals(List.of(FRAME_A), collected);
    }

    @Test
    public void coalescedFramesAreSplit() {
        HsInboundFrameAssembler assembler = new HsInboundFrameAssembler();
        List<String> frames = assembler.accept(FRAME_A + FRAME_B);
        assertEquals(List.of(FRAME_A, FRAME_B), frames);
    }

    @Test
    public void combinedBatteryFixedLengthIsRecognized() {
        HsInboundFrameAssembler assembler = new HsInboundFrameAssembler();
        List<String> frames = assembler.accept(COMBINED_BATTERY);
        assertEquals(List.of(COMBINED_BATTERY), frames);
    }

    @Test
    public void garbagePrefixResyncsToValidFrame() {
        HsInboundFrameAssembler assembler = new HsInboundFrameAssembler();
        List<String> frames = assembler.accept("FF" + FRAME_A);
        assertEquals(List.of(FRAME_A), frames);
    }

    @Test
    public void partialFrameWaitsThenCompletes() {
        HsInboundFrameAssembler assembler = new HsInboundFrameAssembler();
        assertTrue(assembler.accept(FRAME_A.substring(0, 6)).isEmpty());
        assertEquals(List.of(FRAME_A), assembler.accept(FRAME_A.substring(6)));
    }

    @Test
    public void resetDropsPendingBuffer() {
        HsInboundFrameAssembler assembler = new HsInboundFrameAssembler();
        assertTrue(assembler.accept(FRAME_A.substring(0, 6)).isEmpty());
        assembler.reset();
        assertEquals(List.of(FRAME_B), assembler.accept(FRAME_B));
    }
}
