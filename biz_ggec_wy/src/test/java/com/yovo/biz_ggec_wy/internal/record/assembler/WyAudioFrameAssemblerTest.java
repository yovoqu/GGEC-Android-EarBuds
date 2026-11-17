package com.yovo.biz_ggec_wy.internal.record.assembler;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class WyAudioFrameAssemblerTest {

    @Test
    public void testQuad176Assembler() {
        WyAudioFrameAssembler assembler = new WyAudioFrameAssembler();
        byte[] chunk = createQuad176(1, (byte) 0x11);
        List<byte[]> frames = assembler.assemble(chunk);

        assertEquals(WyAudioNotifyFormat.WY_QUAD_40_176, assembler.getActiveFormat());
        assertEquals(4, frames.size());
        assertEquals(4, assembler.getLastDataIndex());
        for (byte[] frame : frames) {
            assertEquals(WyAudioFrameContract.OPUS_BYTES_16K, frame.length);
            assertEquals((byte) 0x11, frame[0]);
        }
    }

    @Test
    public void testDualHalf168Assembler() {
        WyAudioFrameAssembler assembler = new WyAudioFrameAssembler();
        byte[] chunk = createDualHalf168(1, (byte) 0x22);
        List<byte[]> frames = assembler.assemble(chunk);

        assertEquals(WyAudioNotifyFormat.WY_DUAL_HALF_168, assembler.getActiveFormat());
        assertEquals(2, frames.size());
        assertEquals(2, assembler.getLastDataIndex());
        for (byte[] frame : frames) {
            assertEquals(WyAudioFrameContract.OPUS_BYTES_32K, frame.length);
            assertEquals((byte) 0x22, frame[0]);
        }
    }

    @Test
    public void testDualHalf168AssemblerStartingAtZero() {
        WyAudioFrameAssembler assembler = new WyAudioFrameAssembler();
        byte[] chunk1 = createDualHalf168(0, (byte) 0x33);
        List<byte[]> frames1 = assembler.assemble(chunk1);

        assertEquals(WyAudioNotifyFormat.WY_DUAL_HALF_168, assembler.getActiveFormat());
        assertEquals(2, frames1.size());
        assertEquals(1, assembler.getLastDataIndex());

        byte[] chunk2 = createDualHalf168(2, (byte) 0x44);
        List<byte[]> frames2 = assembler.assemble(chunk2);
        assertEquals(2, frames2.size());
        assertEquals(3, assembler.getLastDataIndex());
        for (byte[] frame : frames2) {
            assertEquals(WyAudioFrameContract.OPUS_BYTES_32K, frame.length);
            assertEquals((byte) 0x44, frame[0]);
        }
    }

    @Test
    public void testMtuSplitContinuousStreaming() {
        WyAudioFrameAssembler assembler = new WyAudioFrameAssembler();

        // 168B MTU Split 第一包：164B (4B index1 + 160B opus) + 4B index2
        byte[] chunk1 = new byte[168];
        writeLeUint32(chunk1, 0, 1);
        for (int i = 4; i < 164; i++) chunk1[i] = (byte) 0xAA;
        writeLeUint32(chunk1, 164, 2);

        List<byte[]> frames1 = assembler.assemble(chunk1);
        assertEquals(WyAudioNotifyFormat.WY_MTU_SPLIT, assembler.getActiveFormat());
        assertEquals(1, frames1.size());
        assertEquals(1, assembler.getLastDataIndex());
        assertEquals(160, frames1.get(0).length);

        // 168B MTU Split 第二包：160B opus (for index2) + 8B (4B index3 + 4B spill)
        byte[] chunk2 = new byte[168];
        for (int i = 0; i < 160; i++) chunk2[i] = (byte) 0xBB;
        writeLeUint32(chunk2, 160, 3);
        chunk2[164] = 0x01; chunk2[165] = 0x02; chunk2[166] = 0x03; chunk2[167] = 0x04;

        List<byte[]> frames2 = assembler.assemble(chunk2);
        assertEquals(1, frames2.size());
        assertEquals(2, assembler.getLastDataIndex());
        assertEquals(160, frames2.get(0).length);
        assertEquals((byte) 0xBB, frames2.get(0)[0]);
    }

    private static byte[] createQuad176(int startIndex, byte fill) {
        byte[] chunk = new byte[176];
        int step = 44;
        for (int i = 0; i < 4; i++) {
            writeLeUint32(chunk, i * step, startIndex + i);
            for (int b = 4; b < step; b++) {
                chunk[i * step + b] = fill;
            }
        }
        return chunk;
    }

    private static byte[] createDualHalf168(int startIndex, byte fill) {
        byte[] chunk = new byte[168];
        int step = 84;
        for (int i = 0; i < 2; i++) {
            writeLeUint32(chunk, i * step, startIndex + i);
            for (int b = 4; b < step; b++) {
                chunk[i * step + b] = fill;
            }
        }
        return chunk;
    }

    private static void writeLeUint32(byte[] data, int offset, int value) {
        data[offset] = (byte) (value & 0xFF);
        data[offset + 1] = (byte) ((value >> 8) & 0xFF);
        data[offset + 2] = (byte) ((value >> 16) & 0xFF);
        data[offset + 3] = (byte) ((value >> 24) & 0xFF);
    }
}
