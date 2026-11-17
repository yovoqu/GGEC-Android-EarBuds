package com.yovo.lib_ggec_audio;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 音频帧拆包重组器：从 Notify 数据切片里识别线格式、校验包序号、还原完整的 20ms Opus 编码帧。
 *
 * <p>同族固件（HS / WY）共用同一套空口帧协议：4 字节小端 dataIndex + Opus 帧，一包可含 1~4 帧，
 * 也可能把一帧拆到两个 Notify。装配器按首包锁定线格式，之后只做切帧与序号连续性判断。</p>
 *
 * <p>不满足最小音频子帧（44 字节）的切片一律不进入缓冲：控制上报包混进音频管线会让线格式误判，
 * 并且污染解码缓存。</p>
 */
public final class AudioFrameAssembler {

    private static final int MTU_FIRST_CHUNK = AudioFrameContract.NOTIFY_MTU_FIRST; // 168
    private static final int MTU_SECOND_OPUS_BYTES = AudioFrameContract.OPUS_BYTES_64K; // 160
    private static final int MTU_SECOND_SPILL_BYTES = MTU_FIRST_CHUNK - MTU_SECOND_OPUS_BYTES; // 8
    private static final int FULL_SUBFRAME = AudioFrameContract.SUBFRAME_164; // 164
    private static final int DEFAULT_CAPACITY = 4096;

    private final byte[] buffer;
    private final AudioPacketTracker indexTracker;
    private final AudioFrameTrace trace;
    private int size;
    @Nullable
    private AudioNotifyFormat lockedFormat;

    public AudioFrameAssembler() {
        this(DEFAULT_CAPACITY, AudioFrameTrace.NONE);
    }

    public AudioFrameAssembler(@NonNull AudioFrameTrace trace) {
        this(DEFAULT_CAPACITY, trace);
    }

    public AudioFrameAssembler(int initialCapacity, @NonNull AudioFrameTrace trace) {
        int minCapacity = AudioFrameContract.NOTIFY_LEGACY_DUAL_336 * 2;
        this.buffer = new byte[Math.max(initialCapacity, minCapacity)];
        this.trace = trace;
        this.indexTracker = new AudioPacketTracker(trace);
        this.size = 0;
    }

    public void setAllowIndexResync(boolean allow) {
        indexTracker.setAllowForwardResync(allow);
    }

    public void reset() {
        size = 0;
        lockedFormat = null;
        indexTracker.reset();
    }

    @Nullable
    public AudioNotifyFormat getActiveFormat() {
        return lockedFormat;
    }

    public int pendingBytes() {
        return size;
    }

    public int getLastDataIndex() {
        return indexTracker.getLastDataIndex();
    }

    /**
     * 投喂一个 Notify 切片，返回本次能还原出的完整 Opus 帧。
     *
     * @param chunk 一个 Notify 负载；null 或短于最小音频子帧时返回空表，且不进入缓冲
     */
    @NonNull
    public List<byte[]> assemble(@Nullable byte[] chunk) {
        if (chunk == null || chunk.length < AudioFrameContract.SUBFRAME_44) {
            trace.onFormatUnresolved(chunk == null ? 0 : chunk.length, size);
            return Collections.emptyList();
        }
        ensureFormatLocked(chunk);
        if (lockedFormat == null) {
            appendToBuffer(chunk);
            ensureFormatLocked(chunk);
        }
        if (lockedFormat == null) {
            trace.onFormatUnresolved(chunk.length, size);
            return Collections.emptyList();
        }
        return dispatchByFormat(chunk, lockedFormat);
    }

    private void ensureFormatLocked(byte[] chunk) {
        if (lockedFormat != null) {
            return;
        }
        lockedFormat = AudioFormatDetector.detect(chunk, size, indexTracker);
        if (lockedFormat != null) {
            trace.onFormatLocked(lockedFormat);
        }
    }

    private List<byte[]> dispatchByFormat(byte[] chunk, @NonNull AudioNotifyFormat format) {
        return switch (format) {
            case QUAD_40_176, DECA_40_440 -> appendFixedStep(chunk, format);
            case DUAL_HALF_168 -> appendDualHalf168(chunk);
            case FULL_FRAME -> appendFullFrame(chunk);
            case MTU_SPLIT -> appendMtuSplit(chunk);
            case STANDARD_LEGACY -> appendStandardLegacy(chunk);
        };
    }

    private List<byte[]> appendDualHalf168(byte[] chunk) {
        if (chunk.length != MTU_FIRST_CHUNK || size != 0) {
            appendToBuffer(chunk);
            return extractSteppedFromBuffer(lockedFormat);
        }
        clearPending();
        return extractSteppedFromChunk(chunk, lockedFormat);
    }

    private List<byte[]> appendFullFrame(byte[] chunk) {
        AudioNotifyFormat format = AudioNotifyFormat.FULL_FRAME;
        if (chunk.length == format.getNotifyBytes() && size == 0) {
            clearPending();
            return extractSteppedFromChunk(chunk, format);
        }
        if (chunk.length == format.getSubframeBytes() && size == 0) {
            clearPending();
            return extractSteppedFromChunk(chunk, format);
        }
        if (chunk.length == MTU_FIRST_CHUNK && size == 0) {
            return extractSingleFullFrame168(chunk);
        }
        appendToBuffer(chunk);
        return extractSteppedFromBuffer(format);
    }

    private List<byte[]> appendMtuSplit(byte[] chunk) {
        if (size == 0 && chunk.length == MTU_FIRST_CHUNK) {
            return extractSingleFullFrame168(chunk);
        }
        if (size == AudioFrameContract.HEADER_BYTES && chunk.length == MTU_FIRST_CHUNK) {
            return completeMtuSecondChunk(chunk);
        }
        if (size == AudioFrameContract.HEADER_LEGACY_BYTES && chunk.length == MTU_FIRST_CHUNK) {
            appendToBuffer(chunk);
            return extractFrameAtBufferHeadIfReady();
        }
        appendToBuffer(chunk);
        return extractSteppedFromBuffer(AudioNotifyFormat.FULL_FRAME);
    }

    private List<byte[]> appendStandardLegacy(byte[] chunk) {
        appendToBuffer(chunk);
        return extractSteppedFromBuffer(AudioNotifyFormat.STANDARD_LEGACY);
    }

    /** 168B MTU 首段或单帧：164B 帧 + 可选 4B 下一帧头。 */
    private List<byte[]> extractSingleFullFrame168(byte[] chunk) {
        AudioNotifyFormat format = AudioNotifyFormat.FULL_FRAME;
        List<byte[]> frames = extractSteppedFromChunk(chunk, format);
        int trailerIndex = AudioFormatDetector.readLeUint32(chunk, format.getSubframeBytes());
        if (indexTracker.isExactNext(trailerIndex)) {
            System.arraycopy(chunk, format.getSubframeBytes(), buffer, 0,
                    AudioFrameContract.HEADER_BYTES);
            size = AudioFrameContract.HEADER_BYTES;
        } else {
            clearPending();
        }
        return frames;
    }

    private List<byte[]> appendFixedStep(byte[] chunk, @NonNull AudioNotifyFormat format) {
        int step = format.getSubframeBytes();
        int maxNotify = format.getNotifyBytes();
        if (size == 0 && chunk.length > 0 && chunk.length % step == 0
                && chunk.length <= maxNotify) {
            return extractSteppedFromChunk(chunk, format);
        }
        appendToBuffer(chunk);
        return extractSteppedFromBuffer(format);
    }

    private List<byte[]> extractSteppedFromChunk(byte[] chunk, @NonNull AudioNotifyFormat format) {
        int step = format.getSubframeBytes();
        int count = chunk.length / step;
        List<byte[]> frames = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            byte[] opus = extractOpus(chunk, i * step, format);
            if (opus != null) {
                frames.add(opus);
            }
        }
        return frames;
    }

    private List<byte[]> extractSteppedFromBuffer(@Nullable AudioNotifyFormat format) {
        if (format == null) {
            return Collections.emptyList();
        }
        List<byte[]> frames = new ArrayList<>();
        int step = format.getSubframeBytes();
        while (size >= step) {
            int dataIndex = AudioFormatDetector.readLeUint32(buffer, 0);
            if (!indexTracker.isPlausible(dataIndex)) {
                if (size > 1) {
                    slideOneByte();
                    continue;
                }
                break;
            }
            frames.add(copyOpus(buffer, format.getHeaderBytes(), format.getEncodedOpusBytes()));
            indexTracker.accept(dataIndex);
            consumeBufferHead(step);
        }
        return frames;
    }

    @Nullable
    private byte[] extractOpus(byte[] chunk, int offset, @NonNull AudioNotifyFormat format) {
        int header = format.getHeaderBytes();
        int encoded = format.getEncodedOpusBytes();
        if (offset + header + encoded > chunk.length) {
            return null;
        }
        int dataIndex = AudioFormatDetector.readLeUint32(chunk, offset);
        if (!indexTracker.isPlausible(dataIndex)) {
            return null;
        }
        byte[] opus = copyOpus(chunk, offset + header, encoded);
        indexTracker.accept(dataIndex);
        return opus;
    }

    private byte[] copyOpus(byte[] src, int offset, int encodedBytes) {
        byte[] opus = new byte[encodedBytes];
        System.arraycopy(src, offset, opus, 0, encodedBytes);
        return opus;
    }

    private List<byte[]> completeMtuSecondChunk(byte[] chunk) {
        int dataIndex = AudioFormatDetector.readLeUint32(buffer, 0);
        if (!indexTracker.isExactNext(dataIndex)) {
            clearPending();
            lockedFormat = AudioFormatDetector.detect(chunk, 0, indexTracker);
            if (lockedFormat == null) {
                trace.onFormatUnresolved(chunk.length, size);
                return Collections.emptyList();
            }
            return dispatchByFormat(chunk, lockedFormat);
        }
        byte[] opus = copyOpus(chunk, 0, MTU_SECOND_OPUS_BYTES);
        indexTracker.accept(dataIndex);
        List<byte[]> frames = new ArrayList<>(1);
        frames.add(opus);
        System.arraycopy(chunk, MTU_SECOND_OPUS_BYTES, buffer, 0, MTU_SECOND_SPILL_BYTES);
        size = MTU_SECOND_SPILL_BYTES;
        return frames;
    }

    private List<byte[]> extractFrameAtBufferHeadIfReady() {
        if (size < FULL_SUBFRAME) {
            return Collections.emptyList();
        }
        int dataIndex = AudioFormatDetector.readLeUint32(buffer, 0);
        if (!indexTracker.isPlausible(dataIndex)) {
            slideOneByte();
            return extractSteppedFromBuffer(AudioNotifyFormat.FULL_FRAME);
        }
        AudioNotifyFormat format = AudioNotifyFormat.FULL_FRAME;
        byte[] opus = copyOpus(buffer, format.getHeaderBytes(), format.getEncodedOpusBytes());
        List<byte[]> frames = new ArrayList<>(1);
        frames.add(opus);
        indexTracker.accept(dataIndex);
        consumeBufferHead(FULL_SUBFRAME);
        return frames;
    }

    private void consumeBufferHead(int step) {
        int remaining = size - step;
        if (remaining > 0) {
            System.arraycopy(buffer, step, buffer, 0, remaining);
        }
        size = remaining;
    }

    private void clearPending() {
        size = 0;
    }

    private void slideOneByte() {
        System.arraycopy(buffer, 1, buffer, 0, size - 1);
        size--;
    }

    private void appendToBuffer(byte[] chunk) {
        if (size + chunk.length > buffer.length) {
            int keepFrom = Math.max(0, size + chunk.length - buffer.length);
            int keep = size - keepFrom;
            if (keep > 0) {
                System.arraycopy(buffer, keepFrom, buffer, 0, keep);
            } else {
                keep = 0;
            }
            size = keep;
            if (size + chunk.length > buffer.length) {
                int over = size + chunk.length - buffer.length;
                int copyLen = chunk.length - over;
                if (copyLen <= 0) {
                    System.arraycopy(chunk, chunk.length - buffer.length, buffer, 0, buffer.length);
                    size = buffer.length;
                } else {
                    System.arraycopy(chunk, 0, buffer, size, copyLen);
                    size += copyLen;
                }
            } else {
                System.arraycopy(chunk, 0, buffer, size, chunk.length);
                size += chunk.length;
            }
        } else {
            System.arraycopy(chunk, 0, buffer, size, chunk.length);
            size += chunk.length;
        }
    }
}
