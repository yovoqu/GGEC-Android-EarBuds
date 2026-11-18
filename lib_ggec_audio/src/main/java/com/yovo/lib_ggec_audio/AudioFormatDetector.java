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

    /** 允许的整包丢包倍数上限：差值是每包帧数的 1~4 倍仍可判定。 */
    private static final int MAX_LOSS_MULTIPLE = 4;

    private AudioFormatDetector() {
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
     * 由「包长 + 首帧序号推进量」确定布局（用于包长歧义的场景）。
     *
     * <p>推进量是每包帧数的整数倍（丢整包时成倍）。同一包长下有多个候选时，
     * 取「每包帧数最大且能整除推进量」的那个——它对丢包的容忍度最高，
     * 也最符合「一包尽量多帧」的固件省电设计。</p>
     *
     * @param notifyBytes  稳态 Notify 包长
     * @param indexAdvance 相邻包首帧序号之差
     * @return 匹配的布局；无匹配返回 {@code null}
     */
    @Nullable
    static AudioNotifyFormat formatFor(int notifyBytes, int indexAdvance) {
        if (notifyBytes <= 0 || indexAdvance <= 0) {
            return null;
        }
        AudioNotifyFormat best = null;
        int bestFrames = 0;
        for (AudioNotifyFormat format : AudioNotifyFormat.values()) {
            if (format.getNotifyBytes() != notifyBytes) {
                continue;
            }
            int frames = format.framesCoveredByNotify();
            if (frames <= 0 || indexAdvance % frames != 0) {
                continue;
            }
            if (indexAdvance / frames > MAX_LOSS_MULTIPLE) {
                continue;
            }
            if (frames > bestFrames) {
                best = format;
                bestFrames = frames;
            }
        }
        return best;
    }

    /**
     * 首包长度与稳态长度可能不同（例如首包刚好完整、后续包才带上残段），
     * 两个长度都试一次。
     */
    @Nullable
    static AudioNotifyFormat formatFor(int firstLength, int steadyLength, int indexAdvance) {
        AudioNotifyFormat matched = formatFor(steadyLength, indexAdvance);
        if (matched != null) {
            return matched;
        }
        return formatFor(firstLength, indexAdvance);
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
