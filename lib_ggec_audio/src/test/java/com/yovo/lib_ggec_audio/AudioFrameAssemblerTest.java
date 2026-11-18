package com.yovo.lib_ggec_audio;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AudioFrameAssemblerTest {

    /**
     * HS 基线：小端字节序必须与既有行为完全一致（默认构造器即小端）。
     *
     * <p>字节序是产品适配参数：WY 用大端，但绝不能影响 HS 的小端解析。</p>
     */
    @Test
    public void littleEndianRemainsDefaultForHs() {
        // 默认构造器不传字节序 → 小端。
        AudioFrameAssembler assembler = new AudioFrameAssembler();
        List<byte[]> frames = assembler.assemble(quad176Le(1, (byte) 0x19));

        assertEquals(AudioNotifyFormat.QUAD_40_176, assembler.getActiveFormat());
        assertEquals(4, frames.size());
        assertEquals(4, assembler.getLastDataIndex());
        for (byte[] frame : frames) {
            assertEquals(AudioFrameContract.OPUS_BYTES_16K, frame.length);
            assertEquals((byte) 0x19, frame[0]);
        }
    }

    @Test
    public void locksQuad176AndExtractsFourFrames() {
        AudioFrameAssembler assembler = new AudioFrameAssembler(AudioFrameTrace.NONE, AudioWireByteOrder.BIG_ENDIAN);
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
        AudioFrameAssembler assembler = new AudioFrameAssembler(AudioFrameTrace.NONE, AudioWireByteOrder.BIG_ENDIAN);
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
        AudioFrameAssembler assembler = new AudioFrameAssembler(AudioFrameTrace.NONE, AudioWireByteOrder.BIG_ENDIAN);
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

    /**
     * 现场回归：168 字节一包 4 帧（8B 通知头 + 4×40B Opus），序号 0→4→8→12。
     *
     * <p>修复前这里会锁成 MTU_SPLIT / 整包单帧，每包只出 1 帧 160B，
     * 后续每帧都让 Opus 报 {@code corrupted stream}，并刷「缺口=3」。</p>
     */
    @Test
    public void locksQuad40_168FromFieldSequenceAndExtractsFourFramesPerNotify() {
        RecordingTrace trace = new RecordingTrace();
        AudioFrameAssembler assembler = new AudioFrameAssembler(trace, AudioWireByteOrder.BIG_ENDIAN);

        // 首个包证据不足（看不到相邻包序号差、包内也没有内嵌帧头）：只缓存，不锁定。
        assertTrue(assembler.assemble(quad40_168(0, (byte) 0x51)).isEmpty());
        assertNull(assembler.getActiveFormat());
        assertEquals(AudioFrameContract.NOTIFY_QUAD_40_168, assembler.pendingBytes());

        // 第二包给出序号差 4 → 锁定 4 帧/包；缓存的首包同时被解出，一帧不丢。
        List<byte[]> second = assembler.assemble(quad40_168(4, (byte) 0x52));
        assertEquals(AudioNotifyFormat.QUAD_40_168, assembler.getActiveFormat());
        assertEquals(8, second.size());
        assertEquals(AudioFrameContract.OPUS_BYTES_16K, second.get(0).length);
        assertEquals((byte) 0x51, second.get(0)[0]);
        assertEquals((byte) 0x52, second.get(4)[0]);
        assertEquals(7, assembler.getLastDataIndex());

        // 稳态：每包 4 帧，序号推进 4，且不得报「缺口」。
        List<byte[]> third = assembler.assemble(quad40_168(8, (byte) 0x53));
        assertEquals(4, third.size());
        assertEquals(11, assembler.getLastDataIndex());
        assertEquals(0, trace.gaps.size());

        List<byte[]> fourth = assembler.assemble(quad40_168(12, (byte) 0x54));
        assertEquals(4, fourth.size());
        assertEquals(15, assembler.getLastDataIndex());
        assertEquals(0, trace.gaps.size());
    }

    /** 真实丢整包时才报缺口，且缺口按「每包帧数」折算。 */
    @Test
    public void reportsGapOnlyWhenWholeNotifyIsMissing() {
        RecordingTrace trace = new RecordingTrace();
        AudioFrameAssembler assembler = new AudioFrameAssembler(trace, AudioWireByteOrder.BIG_ENDIAN);

        assembler.assemble(quad40_168(0, (byte) 0x61));
        assembler.assemble(quad40_168(4, (byte) 0x62));
        assembler.assemble(quad40_168(8, (byte) 0x63));
        assertEquals(0, trace.gaps.size());

        // 丢掉 12 这一整包，下一个是 16。
        assembler.assemble(quad40_168(16, (byte) 0x64));
        assertEquals(1, trace.gaps.size());
        assertEquals("8->16", trace.gaps.get(0));
    }

    /** 每帧一个头的 168×2 布局不得被序号差误判成 4 帧整包头。 */
    @Test
    public void keepsDualHalf168DistinctFromQuad40_168() {
        AudioFrameAssembler assembler = new AudioFrameAssembler(AudioFrameTrace.NONE, AudioWireByteOrder.BIG_ENDIAN);
        // 包内 offset 84 有内嵌帧头（idx0+1）→ 首包即可锁定 2 帧 × 84B。
        List<byte[]> first = assembler.assemble(dualHalf168(0, (byte) 0x71));
        assertEquals(AudioNotifyFormat.DUAL_HALF_168, assembler.getActiveFormat());
        assertEquals(2, first.size());
        assertEquals(AudioFrameContract.OPUS_BYTES_32K, first.get(0).length);

        List<byte[]> frames = assembler.assemble(dualHalf168(2, (byte) 0x72));
        assertEquals(2, frames.size());
        assertEquals(AudioFrameContract.OPUS_BYTES_32K, frames.get(0).length);
    }

    @Test
    public void reassemblesMtuSplitStream() {
        AudioFrameAssembler assembler = new AudioFrameAssembler(AudioFrameTrace.NONE, AudioWireByteOrder.BIG_ENDIAN);

        byte[] head = new byte[AudioFrameContract.NOTIFY_MTU_FIRST];
        writeBeUint32(head, 0, 1);
        for (int i = AudioFrameContract.HEADER_BYTES; i < AudioFrameContract.SUBFRAME_164; i++) {
            head[i] = (byte) 0xAA;
        }
        writeBeUint32(head, AudioFrameContract.SUBFRAME_164, 2);

        List<byte[]> firstFrames = assembler.assemble(head);
        assertEquals(AudioNotifyFormat.MTU_SPLIT, assembler.getActiveFormat());
        assertEquals(1, firstFrames.size());
        assertEquals(1, assembler.getLastDataIndex());
        assertEquals(AudioFrameContract.OPUS_BYTES_64K, firstFrames.get(0).length);

        byte[] tail = new byte[AudioFrameContract.NOTIFY_MTU_FIRST];
        for (int i = 0; i < AudioFrameContract.OPUS_BYTES_64K; i++) {
            tail[i] = (byte) 0xBB;
        }
        writeBeUint32(tail, AudioFrameContract.OPUS_BYTES_64K, 3);

        List<byte[]> secondFrames = assembler.assemble(tail);
        assertEquals(1, secondFrames.size());
        assertEquals(2, assembler.getLastDataIndex());
        assertEquals((byte) 0xBB, secondFrames.get(0)[0]);
    }

    @Test
    public void rejectsSubMinimumChunkWithoutPollutingState() {
        AudioFrameAssembler assembler = new AudioFrameAssembler(AudioFrameTrace.NONE, AudioWireByteOrder.BIG_ENDIAN);
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
        AudioFrameAssembler assembler = new AudioFrameAssembler(AudioFrameTrace.NONE, AudioWireByteOrder.BIG_ENDIAN);
        byte[] legacy = new byte[AudioFrameContract.NOTIFY_LEGACY_DUAL_336];
        int step = AudioFrameContract.PACKET_LEGACY_168;
        for (int i = 0; i < AudioFrameContract.FRAMES_PER_NOTIFY_STREAM; i++) {
            writeBeUint32(legacy, i * step, 7 + i);
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

    /** 单帧整包 164B（4B 头 + 160B Opus）必须能首包锁定，不能因包长不等于 328 而漏判。 */
    @Test
    public void locksSingleFrame164AsFullFrame() {
        AudioFrameAssembler assembler = new AudioFrameAssembler(AudioFrameTrace.NONE, AudioWireByteOrder.BIG_ENDIAN);
        byte[] chunk = new byte[AudioFrameContract.SUBFRAME_164];
        writeBeUint32(chunk, 0, 1);
        for (int b = AudioFrameContract.HEADER_BYTES; b < chunk.length; b++) {
            chunk[b] = (byte) 0x77;
        }

        List<byte[]> frames = assembler.assemble(chunk);

        assertEquals(AudioNotifyFormat.FULL_FRAME, assembler.getActiveFormat());
        assertEquals(1, frames.size());
        assertEquals(AudioFrameContract.OPUS_BYTES_64K, frames.get(0).length);
        assertEquals((byte) 0x77, frames.get(0)[0]);
    }

    @Test
    public void traceReportsFormatLockAndForwardGap() {
        RecordingTrace trace = new RecordingTrace();
        AudioFrameAssembler assembler = new AudioFrameAssembler(trace, AudioWireByteOrder.BIG_ENDIAN);

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
        public void onDataIndexGap(int fromIndex, int toIndex, int framesPerNotify) {
            gaps.add(fromIndex + "->" + toIndex);
        }
    }

    private static byte[] quad176(int startIndex, byte fill) {
        byte[] chunk = new byte[AudioFrameContract.NOTIFY_QUAD_176];
        int step = AudioFrameContract.SUBFRAME_44;
        for (int i = 0; i < AudioFrameContract.FRAMES_PER_NOTIFY_LOCAL; i++) {
            writeBeUint32(chunk, i * step, startIndex + i);
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
            writeBeUint32(chunk, i * step, startIndex + i);
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
            writeBeUint32(chunk, i * step, startIndex + i);
            for (int b = AudioFrameContract.HEADER_BYTES; b < step; b++) {
                chunk[i * step + b] = fill;
            }
        }
        return chunk;
    }

    /** 168 字节一包 4 帧：整包一个 8B 通知头，随后 4 个连续 40B Opus。 */
    private static byte[] quad40_168(int startIndex, byte fill) {
        byte[] chunk = new byte[AudioFrameContract.NOTIFY_QUAD_40_168];
        writeBeUint32(chunk, 0, startIndex);
        for (int b = AudioFrameContract.HEADER_LEGACY_BYTES; b < chunk.length; b++) {
            chunk[b] = fill;
        }
        return chunk;
    }

    /** 现场协议为 4 字节大端序号（证据：包首 00 00 00 00 / 00 00 00 0A / 00 00 00 14）。 */
    private static void writeBeUint32(byte[] data, int offset, int value) {
        data[offset] = (byte) ((value >> 24) & 0xFF);
        data[offset + 1] = (byte) ((value >> 16) & 0xFF);
        data[offset + 2] = (byte) ((value >> 8) & 0xFF);
        data[offset + 3] = (byte) (value & 0xFF);
    }

    /** 小端写入：用于 HS 基线用例。 */
    private static void writeLeUint32(byte[] data, int offset, int value) {
        data[offset] = (byte) (value & 0xFF);
        data[offset + 1] = (byte) ((value >> 8) & 0xFF);
        data[offset + 2] = (byte) ((value >> 16) & 0xFF);
        data[offset + 3] = (byte) ((value >> 24) & 0xFF);
    }

    /** 176 字节一包 4 帧，每帧 4B 头 + 40B Opus，头为小端。 */
    private static byte[] quad176Le(int startIndex, byte fill) {
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
}
