package com.yovo.biz_ggec_wy.internal.record.assembler;

import androidx.annotation.NonNull;

import com.yovo.biz_ggec_wy.provider.lib_common.log.WyLog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * WY 音频帧拆包重组器：从数据切片中识别帧格式、校验包序号并还原出完整的 20ms Opus 编码帧。
 */
public final class WyAudioFrameAssembler {

    private static final String TAG = "WyAudioFrameAssembler";

    private static final int MTU_FIRST_CHUNK = WyAudioFrameContract.NOTIFY_MTU_FIRST; // 168
    private static final int MTU_SECOND_OPUS_BYTES = WyAudioFrameContract.OPUS_BYTES_64K; // 160
    private static final int MTU_SECOND_SPILL_BYTES = MTU_FIRST_CHUNK - MTU_SECOND_OPUS_BYTES; // 8
    private static final int FULL_SUBFRAME = WyAudioFrameContract.SUBFRAME_164; // 164

    private final byte[] buffer;
    private int size;
    private WyAudioNotifyFormat lockedFormat;
    private final WyAudioPacketTracker indexTracker = new WyAudioPacketTracker();

    public WyAudioFrameAssembler() {
        this(4096);
    }

    public WyAudioFrameAssembler(int initialCapacity) {
        int minCapacity = WyAudioFrameContract.NOTIFY_LEGACY_DUAL_336 * 2;
        this.buffer = new byte[Math.max(initialCapacity, minCapacity)];
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

    public WyAudioNotifyFormat getActiveFormat() {
        return lockedFormat;
    }

    public int pendingBytes() {
        return size;
    }

    public int getLastDataIndex() {
        return indexTracker.getLastDataIndex();
    }

    @NonNull
    public List<byte[]> assemble(@NonNull byte[] chunk) {
        if (chunk == null || chunk.length == 0) {
            return Collections.emptyList();
        }
        ensureFormatLocked(chunk);
        if (lockedFormat == null) {
            appendToBuffer(chunk);
            ensureFormatLocked(chunk);
        }
        if (lockedFormat == null) {
            return Collections.emptyList();
        }
        return dispatchByFormat(chunk);
    }

    private void ensureFormatLocked(byte[] chunk) {
        if (lockedFormat != null) {
            return;
        }
        lockedFormat = WyAudioFormatDetector.detect(chunk, size, indexTracker);
        if (lockedFormat != null) {
            WyLog.i(TAG, "锁定音频 Notify 格式: " + lockedFormat
                    + " 单帧Opus=" + lockedFormat.getEncodedOpusBytes() + "B");
        }
    }

    private List<byte[]> dispatchByFormat(byte[] chunk) {
        switch (lockedFormat) {
            case WY_QUAD_40_176:
                return appendFixedStep(chunk);
            case WY_DUAL_HALF_168:
                return appendDualHalf168(chunk);
            case WY_FULL_FRAME:
                return appendFullFrame(chunk);
            case WY_MTU_SPLIT:
                return appendMtuSplit(chunk);
            case STANDARD_LEGACY:
            default:
                return appendStandardLegacy(chunk);
        }
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
        WyAudioNotifyFormat format = WyAudioNotifyFormat.WY_FULL_FRAME;
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
        if (size == WyAudioFrameContract.HEADER_BYTES && chunk.length == MTU_FIRST_CHUNK) {
            return completeMtuSecondChunk(chunk);
        }
        if (size == WyAudioFrameContract.HEADER_LEGACY_BYTES && chunk.length == MTU_FIRST_CHUNK) {
            appendToBuffer(chunk);
            return extractFrameAtBufferHeadIfReady();
        }
        appendToBuffer(chunk);
        return extractSteppedFromBuffer(WyAudioNotifyFormat.WY_FULL_FRAME);
    }

    private List<byte[]> appendStandardLegacy(byte[] chunk) {
        appendToBuffer(chunk);
        return extractSteppedFromBuffer(WyAudioNotifyFormat.STANDARD_LEGACY);
    }

    private List<byte[]> extractSingleFullFrame168(byte[] chunk) {
        WyAudioNotifyFormat format = WyAudioNotifyFormat.WY_FULL_FRAME;
        List<byte[]> frames = extractSteppedFromChunk(chunk, format);
        int trailerIndex = readLeUint32(chunk, format.getSubframeBytes());
        if (indexTracker.isExactNext(trailerIndex)) {
            System.arraycopy(chunk, format.getSubframeBytes(), buffer, 0,
                    WyAudioFrameContract.HEADER_BYTES);
            size = WyAudioFrameContract.HEADER_BYTES;
        } else {
            clearPending();
        }
        return frames;
    }

    private List<byte[]> appendFixedStep(byte[] chunk) {
        int step = lockedFormat.getSubframeBytes();
        int maxNotify = lockedFormat.getNotifyBytes();
        if (size == 0 && chunk.length > 0 && chunk.length % step == 0
                && chunk.length <= maxNotify) {
            return extractSteppedFromChunk(chunk, lockedFormat);
        }
        appendToBuffer(chunk);
        return extractSteppedFromBuffer(lockedFormat);
    }

    private List<byte[]> extractSteppedFromChunk(byte[] chunk, WyAudioNotifyFormat format) {
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

    private List<byte[]> extractSteppedFromBuffer(WyAudioNotifyFormat format) {
        List<byte[]> frames = new ArrayList<>();
        int step = format.getSubframeBytes();
        while (size >= step) {
            int dataIndex = readLeUint32(buffer, 0);
            if (!indexTracker.isPlausible(dataIndex)) {
                if (size > 1) {
                    slideOneByte();
                    continue;
                }
                break;
            }
            byte[] opus = copyOpus(buffer, format.getHeaderBytes(), format.getEncodedOpusBytes());
            frames.add(opus);
            indexTracker.accept(dataIndex);
            consumeBufferHead(step);
        }
        return frames;
    }

    private byte[] extractOpus(byte[] chunk, int offset, WyAudioNotifyFormat format) {
        int header = format.getHeaderBytes();
        int encoded = format.getEncodedOpusBytes();
        if (offset + header + encoded > chunk.length) {
            return null;
        }
        int dataIndex = readLeUint32(chunk, offset);
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
        int dataIndex = readLeUint32(buffer, 0);
        if (!indexTracker.isExactNext(dataIndex)) {
            clearPending();
            lockedFormat = WyAudioFormatDetector.detect(chunk, 0, indexTracker);
            return dispatchByFormat(chunk);
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
        int dataIndex = readLeUint32(buffer, 0);
        if (!indexTracker.isPlausible(dataIndex)) {
            slideOneByte();
            return extractSteppedFromBuffer(WyAudioNotifyFormat.WY_FULL_FRAME);
        }
        WyAudioNotifyFormat format = WyAudioNotifyFormat.WY_FULL_FRAME;
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

    private static int readLeUint32(byte[] data, int offset) {
        if (data == null || offset < 0 || offset + 3 >= data.length) {
            return -1;
        }
        return (data[offset] & 0xFF)
                | ((data[offset + 1] & 0xFF) << 8)
                | ((data[offset + 2] & 0xFF) << 16)
                | ((data[offset + 3] & 0xFF) << 24);
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
