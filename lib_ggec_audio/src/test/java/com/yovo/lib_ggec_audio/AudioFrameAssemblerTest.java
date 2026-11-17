package com.yovo.lib_ggec_audio;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AudioFrameAssemblerTest {

    @Test
    public void locksQuad176AndExtractsFourFrames() {
        AudioFrameAssembler assembler = new AudioFrameAssembler();
        List<byte[]> frames = assembler.assemble(quad176(1, (byte) 0x11));

        assertEquals(AudioNotifyFormat.QUAD_40_176, assembler.getActiveFormat());
        assertEquals(4, frames.size());
        assertEquals(4, assembler.getLastDataIndex());
        for (byte[] frame : frames) {
            assertEquals(AudioFrameContract.OPUS_BYTES_16K, frame.length);
            assertEquals((byte) 0x11, frame[0]);
        }
    }

    @Test
    public void locksDeca440AndExtractsTenFrames() {
        AudioFrameAssembler assembler = new AudioFrameAssembler();
        List<byte[]> frames = assembler.assemble(deca440(1, (byte) 0x22));

        assertEquals(AudioNotifyFormat.DECA_40_440, assembler.getActiveFormat());
        assertEquals(10, frames.size());
        assertEquals(10, assembler.getLastDataIndex());
        for (byte[] frame : frames) {
            assertEquals(AudioFrameContract.OPUS_BYTES_16K, frame.length);
            assertEquals((byte) 0x22, frame[0]);
        }
    }

    @Test
    public void locksDualHalf168AndKeepsSequenceAcrossChunks() {
        AudioFrameAssembler assembler = new AudioFrameAssembler();
        List<byte[]> first = assembler.assemble(dualHalf168(0, (byte) 0x33));

        assertEquals(AudioNotifyFormat.DUAL_HALF_168, assembler.getActiveFormat());
        assertEquals(2, first.size());
        assertEquals(1, assembler.getLastDataIndex());

        List<byte[]> second = assembler.assemble(dualHalf168(2, (byte) 0x44));
        assertEquals(2, second.size());
        assertEquals(3, assembler.getLastDataIndex());
        for (byte[] frame : second) {
            assertEquals(AudioFrameContract.OPUS_BYTES_32K, frame.length);
            assertEquals((byte) 0x44, frame[0]);
        }
    }

    @Test
    public void reassemblesMtuSplitStream() {
        AudioFrameAssembler assembler = new AudioFrameAssembler();

        byte[] head = new byte[AudioFrameContract.NOTIFY_MTU_FIRST];
        writeLeUint32(head, 0, 1);
        for (int i = AudioFrameContract.HEADER_BYTES; i < AudioFrameContract.SUBFRAME_164; i++) {
            head[i] = (byte) 0xAA;
        }
        writeLeUint32(head, AudioFrameContract.SUBFRAME_164, 2);

        List<byte[]> firstFrames = assembler.assemble(head);
        assertEquals(AudioNotifyFormat.MTU_SPLIT, assembler.getActiveFormat());
        assertEquals(1, firstFrames.size());
        assertEquals(1, assembler.getLastDataIndex());
        assertEquals(AudioFrameContract.OPUS_BYTES_64K, firstFrames.get(0).length);

        byte[] tail = new byte[AudioFrameContract.NOTIFY_MTU_FIRST];
        for (int i = 0; i < AudioFrameContract.OPUS_BYTES_64K; i++) {
            tail[i] = (byte) 0xBB;
        }
        writeLeUint32(tail, AudioFrameContract.OPUS_BYTES_64K, 3);

        List<byte[]> secondFrames = assembler.assemble(tail);
        assertEquals(1, secondFrames.size());
        assertEquals(2, assembler.getLastDataIndex());
        assertEquals((byte) 0xBB, secondFrames.get(0)[0]);
    }

    @Test
    public void rejectsSubMinimumChunkWithoutPollutingState() {
        AudioFrameAssembler assembler = new AudioFrameAssembler();
        byte[] control = new byte[]{0x05, (byte) 0x80, 0x01, 0x00, 0x01};

        assertTrue(assembler.assemble(control).isEmpty());
        assertEquals(0, assembler.pendingBytes());
        assertNull(assembler.getActiveFormat());

        List<byte[]> frames = assembler.assemble(dualHalf168(0, (byte) 0x55));
        assertEquals(AudioNotifyFormat.DUAL_HALF_168, assembler.getActiveFormat());
        assertEquals(2, frames.size());
        assertEquals(1, assembler.getLastDataIndex());
    }

    @Test
    public void locksLegacy336Layout() {
        AudioFrameAssembler assembler = new AudioFrameAssembler();
        byte[] legacy = new byte[AudioFrameContract.NOTIFY_LEGACY_DUAL_336];
        int step = AudioFrameContract.PACKET_LEGACY_168;
        for (int i = 0; i < AudioFrameContract.FRAMES_PER_NOTIFY_STREAM; i++) {
            writeLeUint32(legacy, i * step, 7 + i);
            for (int b = AudioFrameContract.HEADER_LEGACY_BYTES; b < step; b++) {
                legacy[i * step + b] = (byte) 0x66;
            }
        }

        List<byte[]> frames = assembler.assemble(legacy);

        assertEquals(AudioNotifyFormat.STANDARD_LEGACY, assembler.getActiveFormat());
        assertEquals(2, frames.size());
        for (byte[] frame : frames) {
            assertEquals(AudioFrameContract.OPUS_BYTES_64K, frame.length);
        }
    }

    @Test
    public void traceReportsFormatLockAndForwardGap() {
        RecordingTrace trace = new RecordingTrace();
        AudioFrameAssembler assembler = new AudioFrameAssembler(trace);

        assembler.assemble(dualHalf168(0, (byte) 0x11));
        assembler.assemble(dualHalf168(5, (byte) 0x22));

        assertEquals(List.of(AudioNotifyFormat.DUAL_HALF_168), trace.locked);
        assertEquals(List.of("1->5"), trace.gaps);
    }

    private static final class RecordingTrace implements AudioFrameTrace {

        private final List<AudioNotifyFormat> locked = new ArrayList<>();
        private final List<String> gaps = new ArrayList<>();
        private int unresolved;

        @Override
        public void onFormatLocked(AudioNotifyFormat format) {
            locked.add(format);
        }

        @Override
        public void onFormatUnresolved(int chunkBytes, int pendingBytes) {
            unresolved++;
        }

        @Override
        public void onDataIndexGap(int fromIndex, int toIndex) {
            gaps.add(fromIndex + "->" + toIndex);
        }
    }

    private static byte[] quad176(int startIndex, byte fill) {
        byte[] chunk = new byte[AudioFrameContract.NOTIFY_QUAD_176];
        int step = AudioFrameContract.SUBFRAME_44;
        for (int i = 0; i < AudioFrameContract.FRAMES_PER_NOTIFY_LOCAL; i++) {
            writeLeUint32(chunk, i * step, startIndex + i);
            for (int b = AudioFrameContract.HEADER_BYTES; b < step; b++) {
                chunk[i * step + b] = fill;
            }
        }
        return chunk;
    }

    private static byte[] deca440(int startIndex, byte fill) {
        byte[] chunk = new byte[AudioFrameContract.NOTIFY_BURST_440];
        int step = AudioFrameContract.SUBFRAME_44;
        for (int i = 0; i < AudioFrameContract.FRAMES_PER_BURST_10; i++) {
            writeLeUint32(chunk, i * step, startIndex + i);
            for (int b = AudioFrameContract.HEADER_BYTES; b < step; b++) {
                chunk[i * step + b] = fill;
            }
        }
        return chunk;
    }

    private static byte[] dualHalf168(int startIndex, byte fill) {
        byte[] chunk = new byte[AudioFrameContract.NOTIFY_DUAL_168];
        int step = AudioFrameContract.SUBFRAME_84;
        for (int i = 0; i < AudioFrameContract.FRAMES_PER_NOTIFY_STREAM; i++) {
            writeLeUint32(chunk, i * step, startIndex + i);
            for (int b = AudioFrameContract.HEADER_BYTES; b < step; b++) {
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
