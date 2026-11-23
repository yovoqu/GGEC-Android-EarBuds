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
 * <h3>输入是字节流，不是协议包</h3>
 * <p>SPP 读线程会把当次读到与 {@code available()} 剩余合成一个 batch 回调，单次回调长度
 * <b>不保证</b>等于协议包长：突发时可能是 880/1320，慢速时可能是 300+140。因此本类不假设
 * 「回调边界 == 协议包边界」：每个切片<b>无条件</b>追加进累计缓冲，格式锁定改成在<b>累计缓冲</b>
 * 上做几何自证（{@link AudioFormatDetector#scanStream}），与单次切片长度无关。切帧也一律在
 * 累计缓冲上按步长推进。</p>
 *
 * <h3>切帧几何</h3>
 * <ol>
 *   <li><b>每帧一个头、步长固定</b>（176 / 440 / 168×2 / 328 / 旧 336 / MTU 拆分）：
 *       相邻切片首尾相接就是连续字节流，按固定步长推进即可；MTU 拆分（包 168、步长 164）
 *       同样只是步长 164 的连续字节流，包边界漂移不影响按步长切帧；</li>
 *   <li><b>整包一个头、包内 N 帧 Opus 连续</b>（168 = 8B 头 + 4×40B）：
 *       一包一次推进，包内帧序号隐式递增。</li>
 * </ol>
 */
public final class AudioFrameAssembler {

    /** 累计缓冲初始容量：合批/探测都需要足够余量，避免丢头。 */
    private static final int DEFAULT_CAPACITY = 16 * 1024;

    /** 未锁定格式时最多缓存多少字节；超过说明输入根本不是音频流，丢弃重新取证。 */
    private static final int MAX_PROBE_BYTES = AudioFrameContract.NOTIFY_BURST_440 * 8;

    private final byte[] buffer;
    private final AudioPacketTracker indexTracker;
    private final AudioFrameTrace trace;
    /** 帧头字节序：产品适配参数，HS 小端 / WY 大端。 */
    @NonNull
    private final AudioWireByteOrder byteOrder;
    private int size;
    @Nullable
    private AudioNotifyFormat lockedFormat;

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
        this.buffer = new byte[Math.max(initialCapacity, DEFAULT_CAPACITY)];
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
     * 投喂一个 Notify / SPP 切片，返回本次能还原出的完整 Opus 帧。
     *
     * <p>切片无论长短都先追加进累计缓冲（{@code null}/空除外），锁定与切帧都在累计缓冲上做，
     * 因此合批、拆包、前导垃圾滑移都不会丢头失步。</p>
     *
     * @param chunk 一个 Notify / SPP 切片；{@code null} 或空时不影响缓冲，直接返回空表
     */
    @NonNull
    public List<byte[]> assemble(@Nullable byte[] chunk) {
        if (chunk == null || chunk.length == 0) {
            trace.onFormatUnresolved(chunk == null ? 0 : chunk.length, size);
            return Collections.emptyList();
        }

        appendToBuffer(chunk);

        if (lockedFormat == null) {
            tryLockFormat(chunk.length);
        }

        List<byte[]> frames;
        if (lockedFormat == null) {
            if (size > MAX_PROBE_BYTES) {
                clearPending();
                trace.onFormatUnresolved(chunk.length, 0);
            }
            frames = Collections.emptyList();
        } else {
            frames = drainFrames(lockedFormat);
        }

        int firstIndex = AudioFormatDetector.readIndex(chunk, 0, byteOrder);
        trace.onChunkProcessed(chunk, frames.size(), firstIndex);
        return frames;
    }

    /**
     * 从累计缓冲里锁定线格式。
     *
     * <p>判定顺序（证据由强到弱，且始终与 chunk 长度解耦）：</p>
     * <ol>
     *   <li><b>整包长度快路径</b>：仅 176/440 这类 40B 子帧整包（与真实流步长 44 一致），
     *       保留既有枚举断言；单子帧长度（44/84/164）绝不走快路径。</li>
     *   <li><b>几何自证</b>：在累计缓冲上滑窗扫描（{@link AudioFormatDetector#scanStream}），
     *       校验相邻帧头序号按步长连续递增；支持任意分片，并顺带剔除非零起始偏移前的前导垃圾。</li>
     *   <li><b>单帧整包 164B</b>：仅当上面两步都证据不足时，按历史兼容格式放行。</li>
     * </ol>
     */
    private void tryLockFormat(int chunkLength) {
        // 1) 「整包长度族」快路径：仅 176/440 这类 40B 子帧几何（与真实流步长 44 一致），
        //    保留既有 QUAD_40_176 / DECA_40_440 枚举断言；绝不接受单子帧长度（44/84/164），
        //    否则一个更大包的首片（如 164B）会被误锁成 160B 步长，整场坏帧。
        if (size == chunkLength
                && (chunkLength == AudioFrameContract.NOTIFY_QUAD_176
                    || chunkLength == AudioFrameContract.NOTIFY_BURST_440)
                && AudioFormatDetector.readIndex(buffer, 0, byteOrder)
                        >= AudioFrameContract.STARTING_DATA_INDEX) {
            AudioNotifyFormat exact = AudioFormatDetector.exactNotifyFormatFor(chunkLength);
            if (exact != null) {
                lockFormat(exact, 0);
                return;
            }
        }

        // 2) 多帧几何自证优先：在累计缓冲上按步长验证相邻帧头序号连续递增。
        //    合批/拆包/前导垃圾都靠这里，且与 chunk 长度无关。
        AudioFormatDetector.StreamLock streamLock =
                AudioFormatDetector.scanStream(buffer, size, byteOrder);
        if (streamLock != null) {
            lockFormat(streamLock.format, streamLock.startOffset);
            return;
        }

        // 3) 证据不足时，仅对「单帧整包 164B」这一历史兼容格式放行（HS 旧线格式）。
        //    若它是某个更大包的首片，上面 scanStream 已能凭多帧几何锁定，不会走到这里。
        if (size == chunkLength
                && chunkLength == AudioFrameContract.SUBFRAME_164
                && AudioFormatDetector.readIndex(buffer, 0, byteOrder)
                        >= AudioFrameContract.STARTING_DATA_INDEX) {
            lockFormat(AudioNotifyFormat.FULL_FRAME, 0);
        }
    }

    private void lockFormat(@NonNull AudioNotifyFormat format, int startOffset) {
        if (startOffset > 0) {
            consumeBufferHead(startOffset);
        }
        lockedFormat = format;
        indexTracker.setFramesPerNotify(format.framesCoveredByNotify());
        trace.onFormatLocked(format);
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

    /**
     * 每帧一个头、步长固定：字节流连续，按固定步长推进（MTU 拆分的步长 164 也走这里）。
     */
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
