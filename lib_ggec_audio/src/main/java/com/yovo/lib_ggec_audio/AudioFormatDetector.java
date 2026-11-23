package com.yovo.lib_ggec_audio;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 音频 Notify 线格式判定：把「包长」与「每包承载帧数」映射到唯一布局。
 *
 * <p>两种判定路径，按证据强度使用：</p>
 * <ol>
 *   <li><b>包长唯一</b>：176 / 440 / 328 / 164 / 336 各自只对应一种布局，首包即可锁定；</li>
 *   <li><b>包长歧义</b>：168 字节同时对应两种完全不同的布局——
 *       <ul>
 *         <li>2 帧 × 84B（每帧 4B 头 + 80B Opus，32 kbps）；</li>
 *         <li>4 帧 × 40B（整包一个 8B 头 + 4 个连续 40B Opus，16 kbps）。</li>
 *       </ul>
 *       此时必须靠「相邻两个 Notify 首帧 dataIndex 之差」定夺：差就是上一包承载的帧数。
 *       现场日志 {@code 0→4→8→12} 恒定差 4，因此 168 是 4 帧 × 40B，
 *       而不是 2 帧 × 84B，更不是「整包一帧 160B」。</li>
 * </ol>
 *
 * <p>此前缺第 2 条证据，168 在包内找不到内嵌帧头时被猜成单帧 160B，
 * 每包只切出 1 帧，直接导致 Opus {@code corrupted stream}。</p>
 */
final class AudioFormatDetector {

    /** 前导垃圾滑移上限：锁定时最多跳过这么多字节的前导控制/错位字节。 */
    private static final int MAX_SKIP_BYTES = 64;

    private AudioFormatDetector() {
    }

    /**
     * 累计缓冲上的一次格式锁定结果：命中的格式与有效起始偏移。
     *
     * <p>{@code startOffset > 0} 说明起始处有 {@code startOffset} 个前导垃圾字节需要先剔除。</p>
     */
    static final class StreamLock {

        @NonNull
        final AudioNotifyFormat format;
        final int startOffset;

        StreamLock(@NonNull AudioNotifyFormat format, int startOffset) {
            this.format = format;
            this.startOffset = startOffset;
        }
    }

    /**
     * 在<b>累计缓冲</b>上做几何自证，锁定线格式（与单次切片长度无关）。
     *
     * <p>SPP 是字节流，单次回调长度不代表协议包边界；这里只看累计缓冲里的字节几何：对候选
     * 起始偏移 {@code p = 0..MAX_SKIP_BYTES} 读取首帧序号 {@code idx}，再按步长检查后续帧头序号
     * 是否严格 {@code idx+1, idx+2, idx+3...}。连续帧头按步长递增是可靠几何证据，随机负载极难伪造，
     * 因此可以同时解决「合批/拆包」与「前导控制字节滑移」两件事。</p>
     *
     * <p>按确认强度与步长从小到大依序验证（44 → 84 → 164 → 168），全部通过才命中；证据不足
     * （字节还不够做后续校验）时返回 {@code null} 等更多数据。</p>
     *
     * @param buf       累计缓冲
     * @param size      累计缓冲中的有效字节数（{@code buf} 物理长度可能更大，不得越界读）
     * @param byteOrder 帧头字节序
     * @return 命中的格式与起始偏移；证据不足返回 {@code null}
     */
    @Nullable
    static StreamLock scanStream(@NonNull byte[] buf, int size, @NonNull AudioWireByteOrder byteOrder) {
        int maxStart = Math.min(MAX_SKIP_BYTES, size - AudioFrameContract.HEADER_BYTES);
        for (int p = 0; p <= maxStart; p++) {
            int idx = readIndex(buf, p, byteOrder);
            if (idx < AudioFrameContract.STARTING_DATA_INDEX) {
                continue;
            }

            // stride 44：DECA_40_440（同样覆盖 QUAD_40_176 的 40B 子帧几何）。
            if (p + AudioFrameContract.SUBFRAME_44 * 3 + AudioFrameContract.HEADER_BYTES <= size
                    && readIndex(buf, p + AudioFrameContract.SUBFRAME_44, byteOrder) == idx + 1
                    && readIndex(buf, p + AudioFrameContract.SUBFRAME_44 * 2, byteOrder) == idx + 2
                    && readIndex(buf, p + AudioFrameContract.SUBFRAME_44 * 3, byteOrder) == idx + 3) {
                return new StreamLock(AudioNotifyFormat.DECA_40_440, p);
            }

            // stride 84：DUAL_HALF_168（每帧一个头，18 字节内嵌帧头；不足第二帧则单点确认）。
            if (p + AudioFrameContract.SUBFRAME_84 + AudioFrameContract.HEADER_BYTES <= size
                    && readIndex(buf, p + AudioFrameContract.SUBFRAME_84, byteOrder) == idx + 1
                    && (p + AudioFrameContract.NOTIFY_DUAL_168 + AudioFrameContract.HEADER_BYTES > size
                        || readIndex(buf, p + AudioFrameContract.NOTIFY_DUAL_168, byteOrder) == idx + 2)) {
                return new StreamLock(AudioNotifyFormat.DUAL_HALF_168, p);
            }

            // stride 164：MTU_SPLIT（与 FULL_FRAME 同一 164 子帧几何）。
            if (p + AudioFrameContract.SUBFRAME_164 + AudioFrameContract.HEADER_BYTES <= size
                    && readIndex(buf, p + AudioFrameContract.SUBFRAME_164, byteOrder) == idx + 1
                    && (p + AudioFrameContract.SUBFRAME_164 * 2 + AudioFrameContract.HEADER_BYTES > size
                        || readIndex(buf, p + AudioFrameContract.SUBFRAME_164 * 2, byteOrder) == idx + 2)) {
                return new StreamLock(AudioNotifyFormat.MTU_SPLIT, p);
            }

            // stride 168：歧义消解——差 1 为旧 8B 头逐帧布局，差 4 为整包一个 8B 头。
            if (p + AudioFrameContract.NOTIFY_QUAD_40_168 + AudioFrameContract.HEADER_BYTES <= size) {
                int at168 = readIndex(buf, p + AudioFrameContract.NOTIFY_QUAD_40_168, byteOrder);
                if (at168 == idx + 1) {
                    return new StreamLock(AudioNotifyFormat.STANDARD_LEGACY, p);
                }
                if (at168 == idx + 4) {
                    return new StreamLock(AudioNotifyFormat.QUAD_40_168, p);
                }
            }
        }
        return null;
    }

    /**
     * 包长是否能唯一确定**切帧几何**。
     *
     * <p>判定标准不是「布局名字唯一」，而是「切出来的字节序列唯一」：</p>
     * <ul>
     *   <li>{@code FULL_FRAME} 与 {@code MTU_SPLIT} 包长同为 328、步长同为 164、
     *       帧长同为 160 —— 切帧结果完全一致，只是跨包处理策略不同，可以合并，
     *       取不跨包的 {@code FULL_FRAME}；</li>
     *   <li>{@code DUAL_HALF_168}（2×84B）与 {@code QUAD_40_168}（8B 头 + 4×40B）
     *       包长同为 168 但几何完全不同 —— <b>必须返回 null</b>，
     *       交给装配器用「相邻包首帧序号差」定夺。</li>
     * </ul>
     *
     * <p>除整包长度外也接受单帧长度（164 = 4B 头 + 160B Opus 的单帧整包）。</p>
     *
     * @return 几何唯一的布局；包长未知或几何有歧义时返回 {@code null}
     */
    @Nullable
    static AudioNotifyFormat uniqueFormatFor(int packetLength) {
        AudioNotifyFormat exact = null;
        boolean exactAmbiguous = false;
        AudioNotifyFormat byStride = null;
        boolean strideAmbiguous = false;

        for (AudioNotifyFormat format : AudioNotifyFormat.values()) {
            if (!matchesPacketLength(format, packetLength)) {
                continue;
            }
            if (format.getNotifyBytes() == packetLength) {
                if (exact == null) {
                    exact = format;
                } else if (!sameGeometry(exact, format)) {
                    exactAmbiguous = true;
                }
            } else if (byStride == null) {
                byStride = format;
            } else if (!sameGeometry(byStride, format)) {
                strideAmbiguous = true;
            }
        }

        if (exact != null) {
            return exactAmbiguous ? null : canonical(exact);
        }
        if (byStride != null) {
            return strideAmbiguous ? null : canonical(byStride);
        }
        return null;
    }

    /**
     * 只按「整包长度」匹配布局（{@code notifyBytes == packetLength}），且几何必须唯一。
     *
     * <p>与 {@link #uniqueFormatFor(int)} 的关键区别：<b>不接受单帧/子帧长度</b>。
     * 单帧长度（44/84/164）可能只是某个更大包的首片，若据此锁定会选错步长；因此单帧长度
     * 一律交给 {@link #scanStream} 用多帧几何自证判定。</p>
     *
     * @return 由整包长度唯一确定的布局；无匹配或几何歧义（如 168）返回 {@code null}
     */
    @Nullable
    static AudioNotifyFormat exactNotifyFormatFor(int packetLength) {
        AudioNotifyFormat found = null;
        boolean ambiguous = false;
        for (AudioNotifyFormat format : AudioNotifyFormat.values()) {
            if (format.getNotifyBytes() != packetLength) {
                continue;
            }
            if (found == null) {
                found = format;
            } else if (!sameGeometry(found, format)) {
                ambiguous = true;
            }
        }
        if (found == null || ambiguous) {
            return null;
        }
        return canonical(found);
    }

    /** 两个布局切出来的字节序列是否完全一致（布局、帧长、步长都相同）。 */
    private static boolean sameGeometry(@NonNull AudioNotifyFormat a,
                                        @NonNull AudioNotifyFormat b) {
        return a.getLayout() == b.getLayout()
                && a.getEncodedOpusBytes() == b.getEncodedOpusBytes()
                && a.getFrameStrideBytes() == b.getFrameStrideBytes();
    }

    /** 几何等价的候选里取规范代表：优先不跨包的 FULL_FRAME。 */
    @NonNull
    private static AudioNotifyFormat canonical(@NonNull AudioNotifyFormat format) {
        if (format == AudioNotifyFormat.MTU_SPLIT) {
            return AudioNotifyFormat.FULL_FRAME;
        }
        return format;
    }

    /** 该布局是否可能以这个长度出现（整包长度或单帧长度）。 */
    private static boolean matchesPacketLength(@NonNull AudioNotifyFormat format, int length) {
        if (format.getNotifyBytes() == length) {
            return true;
        }
        // 单帧长度只对「每帧一个头」的布局成立；整包头布局的单帧不带头。
        return format.getLayout() == AudioNotifyFormat.FrameLayout.PER_FRAME_HEADER
                && format.getSubframeBytes() == length;
    }

    /**
     * 读取帧头里的 4 字节序号，字节序由产品声明（{@link AudioWireByteOrder}）。
     *
     * <p>大端固件的现场证据：包首 {@code 00 00 00 00} / {@code 00 00 00 0A} / {@code 00 00 00 14}
     * 即 0 / 10 / 20；小端固件沿用既有行为。共享层不写死字节序，避免各产品互相牵制。</p>
     *
     * @return uint32 序号；越界返回 -1
     */
    static int readIndex(byte[] data, int offset, @NonNull AudioWireByteOrder byteOrder) {
        if (data == null || offset < 0 || offset + 3 >= data.length) {
            return -1;
        }
        int b0 = data[offset] & 0xFF;
        int b1 = data[offset + 1] & 0xFF;
        int b2 = data[offset + 2] & 0xFF;
        int b3 = data[offset + 3] & 0xFF;
        if (byteOrder == AudioWireByteOrder.BIG_ENDIAN) {
            return (b0 << 24) | (b1 << 16) | (b2 << 8) | b3;
        }
        return b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
    }
}
