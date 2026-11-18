package com.yovo.lib_ggec_audio;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 音频帧拆包重组器：从 Notify 数据切片里识别线格式、校验包序号、还原完整的 20ms Opus 编码帧。
 *
 * <p>同族固件（HS / WY）共用同一套空口帧协议：4 字节 dataIndex + Opus 帧，一包可含 1~4 帧，
 * 也可能把一帧拆到两个 Notify。</p>
 *
 * <h3>切帧只有三种几何</h3>
 * <ol>
 *   <li><b>每帧一个头、包长是步长的整数倍</b>（176 / 440 / 168×2 / 328 / 旧 336）：
 *       相邻 Notify 首尾相接就是连续字节流，按固定步长推进即可；</li>
 *   <li><b>整包一个头、包内 N 帧 Opus 连续</b>（168 = 8B 头 + 4×40B）：
 *       一包一次推进，包内帧序号隐式递增；</li>
 *   <li><b>MTU 拆分</b>（包 168、步长 164）：步长不是包长的整数倍，包边界必然漂移，
 *       需要跨包补帧的专用状态机。</li>
 * </ol>
 *
 * <p>此前把第 2 种误判成「整包单帧 160B」，于是每包只切出 1 帧，交给 Opus 直接
 * {@code corrupted stream}，并且每包都被报成「前向跳号 缺口=3」——那 3 帧其实就在同一包内。</p>
 */
public final class AudioFrameAssembler {

    private static final int DEFAULT_CAPACITY = 4096;

    /** MTU 拆分：首包 164B 帧 + 4B 下一帧头。 */
    private static final int MTU_FIRST_CHUNK = AudioFrameContract.NOTIFY_MTU_FIRST;
    private static final int MTU_SECOND_OPUS_BYTES = AudioFrameContract.OPUS_BYTES_64K;
    private static final int MTU_SECOND_SPILL_BYTES = MTU_FIRST_CHUNK - MTU_SECOND_OPUS_BYTES;
    private static final int FULL_SUBFRAME = AudioFrameContract.SUBFRAME_164;

    /** 未锁定格式时最多缓存多少字节；超过说明输入根本不是音频流，丢弃重新取证。 */
    private static final int MAX_PROBE_BYTES = AudioFrameContract.NOTIFY_BURST_440 * 2;

    private final byte[] buffer;
    private final AudioPacketTracker indexTracker;
    private final AudioFrameTrace trace;
    /** 帧头字节序：产品适配参数，HS 小端 / WY 大端。 */
    @NonNull
    private final AudioWireByteOrder byteOrder;
    private int size;
    @Nullable
    private AudioNotifyFormat lockedFormat;

    /** 未锁定期间记录的首包证据：首帧序号与包长。 */
    private int probeIndex = -1;
    private int probeLength = -1;

    public AudioFrameAssembler() {
        this(DEFAULT_CAPACITY, AudioFrameTrace.NONE, AudioWireByteOrder.LITTLE_ENDIAN);
    }

    public AudioFrameAssembler(@NonNull AudioFrameTrace trace) {
        this(DEFAULT_CAPACITY, trace, AudioWireByteOrder.LITTLE_ENDIAN);
    }

    public AudioFrameAssembler(@NonNull AudioFrameTrace trace,
                               @NonNull AudioWireByteOrder byteOrder) {
        this(DEFAULT_CAPACITY, trace, byteOrder);
    }

    public AudioFrameAssembler(int initialCapacity, @NonNull AudioFrameTrace trace) {
        this(initialCapacity, trace, AudioWireByteOrder.LITTLE_ENDIAN);
    }

    public AudioFrameAssembler(int initialCapacity,
                               @NonNull AudioFrameTrace trace,
                               @NonNull AudioWireByteOrder byteOrder) {
        int minCapacity = AudioFrameContract.NOTIFY_LEGACY_DUAL_336 * 2;
        this.buffer = new byte[Math.max(initialCapacity, minCapacity)];
        this.trace = trace;
        this.byteOrder = byteOrder;
        this.indexTracker = new AudioPacketTracker(trace);
        this.size = 0;
    }

    public void setAllowIndexResync(boolean allow) {
        indexTracker.setAllowForwardResync(allow);
    }

    public void reset() {
        size = 0;
        lockedFormat = null;
        probeIndex = -1;
        probeLength = -1;
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
        List<byte[]> frames = assembleInternal(chunk);
        int firstIndex = AudioFormatDetector.readIndex(chunk, 0, byteOrder);
        trace.onChunkProcessed(chunk, frames.size(), firstIndex);
        return frames;
    }

    @NonNull
    private List<byte[]> assembleInternal(@NonNull byte[] chunk) {
        if (lockedFormat == null) {
            AudioNotifyFormat detected = detect(chunk);
            if (detected == null) {
                // 证据不足（例如只有一个包，看不到相邻包序号差）：先缓存，等下一包。
                appendToBuffer(chunk);
                if (size > MAX_PROBE_BYTES) {
                    clearPending();
                    probeIndex = -1;
                    probeLength = -1;
                }
                return Collections.emptyList();
            }
            lockedFormat = detected;
            indexTracker.setFramesPerNotify(detected.framesCoveredByNotify());
            trace.onFormatLocked(detected);
        }
        if (lockedFormat == AudioNotifyFormat.MTU_SPLIT) {
            return appendMtuSplit(chunk);
        }
        appendToBuffer(chunk);
        return drainFrames(lockedFormat);
    }

    /**
     * 判定线格式，两条路径按证据强度使用：
     * <ol>
     *   <li><b>包长唯一</b>（176 / 440 / 328 / 164 / 336）：首包即锁定，不必等第二个包；</li>
     *   <li><b>包长歧义</b>（168 同时对应「2×84B」与「整包 8B 头 + 4×40B」）：
     *       靠相邻包首帧序号差定夺，差就是上一包承载的帧数。</li>
     * </ol>
     *
     * <p>包内嵌帧头（offset 84 / 164 出现「下一帧序号」）作为次级证据：
     * 它能把 MTU 拆分从 168 家族里分出来，但不足以单独区分「整包一个头」的布局，
     * 因为包内随机字节可能偶然命中。</p>
     *
     * @return {@code null} 表示证据不足，需要等下一个包
     */
    @Nullable
    private AudioNotifyFormat detect(byte[] chunk) {
        int index = AudioFormatDetector.readIndex(chunk, 0, byteOrder);
        if (index < AudioFrameContract.STARTING_DATA_INDEX) {
            return null;
        }

        // 路径一：包长唯一 → 首包即可锁定。
        AudioNotifyFormat unique = AudioFormatDetector.uniqueFormatFor(chunk.length);
        if (unique != null) {
            return unique;
        }

        // 路径二：包长歧义（168），先看包内嵌帧头。
        boolean embeddedMtu = chunk.length >= AudioFrameContract.NOTIFY_QUAD_40_168
                && AudioFormatDetector.readIndex(chunk, AudioFrameContract.SUBFRAME_164, byteOrder)
                == index + 1;
        boolean embeddedDual = chunk.length >= AudioFrameContract.NOTIFY_QUAD_40_168
                && AudioFormatDetector.readIndex(chunk, AudioFrameContract.SUBFRAME_84, byteOrder)
                == index + 1;

        if (probeIndex < 0) {
            probeIndex = index;
            probeLength = chunk.length;
            if (embeddedMtu) {
                return AudioNotifyFormat.MTU_SPLIT;
            }
            if (embeddedDual) {
                return AudioNotifyFormat.DUAL_HALF_168;
            }
            return null;
        }

        int advance = index - probeIndex;
        if (advance > 0) {
            AudioNotifyFormat byAdvance =
                    AudioFormatDetector.formatFor(probeLength, chunk.length, advance);
            if (byAdvance != null) {
                return byAdvance;
            }
        }
        if (embeddedMtu) {
            return AudioNotifyFormat.MTU_SPLIT;
        }
        if (embeddedDual) {
            return AudioNotifyFormat.DUAL_HALF_168;
        }

        // 序号回退或完全无匹配：重新取证，避免拿错误的历史做判定。
        probeIndex = index;
        probeLength = chunk.length;
        return null;
    }

    @NonNull
    private List<byte[]> drainFrames(@NonNull AudioNotifyFormat format) {
        List<byte[]> frames = new ArrayList<>();
        if (format.getLayout() == AudioNotifyFormat.FrameLayout.NOTIFY_HEADER) {
            drainNotifyHeaderLayout(format, frames);
        } else {
            drainPerFrameHeaderLayout(format, frames);
        }
        return frames;
    }

    /** 整包一个头：一个 Notify 内 N 帧 Opus 连续排列，帧序号隐式递增。 */
    private void drainNotifyHeaderLayout(@NonNull AudioNotifyFormat format,
                                         @NonNull List<byte[]> frames) {
        int notifyBytes = format.getNotifyBytes();
        int framesPerNotify = format.getFramesPerNotify();
        while (size >= notifyBytes) {
            int index = AudioFormatDetector.readIndex(buffer, 0, byteOrder);
            if (!indexTracker.isPlausible(index)) {
                if (size > 1) {
                    slideOneByte();
                    continue;
                }
                break;
            }
            for (int i = 0; i < framesPerNotify; i++) {
                int offset = format.payloadOffsetOf(i);
                if (offset + format.getEncodedOpusBytes() > notifyBytes) {
                    break;
                }
                frames.add(copyOpus(buffer, offset, format.getEncodedOpusBytes()));
            }
            indexTracker.acceptNotify(index, framesPerNotify);
            consumeBufferHead(notifyBytes);
        }
    }

    /** 每帧一个头、包长为步长整数倍：字节流连续，按固定步长推进。 */
    @NonNull
    private List<byte[]> drainPerFrameHeaderLayout(@NonNull AudioNotifyFormat format) {
        List<byte[]> frames = new ArrayList<>();
        drainPerFrameHeaderLayout(format, frames);
        return frames;
    }

    private void drainPerFrameHeaderLayout(@NonNull AudioNotifyFormat format,
                                           @NonNull List<byte[]> frames) {
        int stride = format.getFrameStrideBytes();
        if (stride <= 0) {
            return;
        }
        while (size >= stride) {
            int index = AudioFormatDetector.readIndex(buffer, 0, byteOrder);
            if (!indexTracker.isPlausible(index)) {
                if (size > 1) {
                    slideOneByte();
                    continue;
                }
                break;
            }
            frames.add(copyOpus(buffer, format.getHeaderBytes(), format.getEncodedOpusBytes()));
            indexTracker.accept(index);
            consumeBufferHead(stride);
        }
    }

    // ---------------------------------------------------------------------
    // MTU 拆分：包长 168、步长 164，步长不是包长的整数倍，包边界跨帧漂移。
    // 这段逻辑是现场验证过的专用状态机，不与上面的整数倍路径混用。
    // ---------------------------------------------------------------------

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
        return drainPerFrameHeaderLayout(AudioNotifyFormat.MTU_SPLIT);
    }

    /** 168B MTU 首段或单帧：164B 帧 + 可选 4B 下一帧头。 */
    private List<byte[]> extractSingleFullFrame168(byte[] chunk) {
        AudioNotifyFormat format = AudioNotifyFormat.MTU_SPLIT;
        List<byte[]> frames = new ArrayList<>(1);
        int index = AudioFormatDetector.readIndex(chunk, 0, byteOrder);
        if (indexTracker.isPlausible(index)) {
            frames.add(copyOpus(chunk, format.getHeaderBytes(), format.getEncodedOpusBytes()));
            indexTracker.accept(index);
        }
        int trailerIndex = AudioFormatDetector.readIndex(chunk, format.getSubframeBytes(), byteOrder);
        if (indexTracker.isExactNext(trailerIndex)) {
            System.arraycopy(chunk, format.getSubframeBytes(), buffer, 0,
                    AudioFrameContract.HEADER_BYTES);
            size = AudioFrameContract.HEADER_BYTES;
        } else {
            clearPending();
        }
        return frames;
    }

    private List<byte[]> completeMtuSecondChunk(byte[] chunk) {
        int dataIndex = AudioFormatDetector.readIndex(buffer, 0, byteOrder);
        if (!indexTracker.isExactNext(dataIndex)) {
            clearPending();
            AudioNotifyFormat reDetected = detect(chunk);
            if (reDetected == null) {
                appendToBuffer(chunk);
                return Collections.emptyList();
            }
            lockedFormat = reDetected;
            indexTracker.setFramesPerNotify(reDetected.framesCoveredByNotify());
            trace.onFormatLocked(reDetected);
            appendToBuffer(chunk);
            if (reDetected == AudioNotifyFormat.MTU_SPLIT) {
                return drainPerFrameHeaderLayout(reDetected);
            }
            return drainFrames(reDetected);
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
        int dataIndex = AudioFormatDetector.readIndex(buffer, 0, byteOrder);
        if (!indexTracker.isPlausible(dataIndex)) {
            slideOneByte();
            return drainPerFrameHeaderLayout(AudioNotifyFormat.MTU_SPLIT);
        }
        byte[] opus = copyOpus(buffer, AudioFrameContract.HEADER_BYTES,
                AudioNotifyFormat.MTU_SPLIT.getEncodedOpusBytes());
        List<byte[]> frames = new ArrayList<>(1);
        frames.add(opus);
        indexTracker.accept(dataIndex);
        consumeBufferHead(FULL_SUBFRAME);
        return frames;
    }

    // ---------------------------------------------------------------------

    private byte[] copyOpus(byte[] src, int offset, int encodedBytes) {
        byte[] opus = new byte[encodedBytes];
        System.arraycopy(src, offset, opus, 0, encodedBytes);
        return opus;
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
