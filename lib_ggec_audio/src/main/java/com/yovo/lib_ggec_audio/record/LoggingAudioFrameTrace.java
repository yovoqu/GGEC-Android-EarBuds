package com.yovo.lib_ggec_audio.record;

import androidx.annotation.NonNull;

import com.yovo.lib_ggec_audio.AudioFrameTrace;
import com.yovo.lib_ggec_audio.AudioNotifyFormat;
import com.yovo.lib_ggec_audio.AudioWireByteOrder;

import java.util.Locale;

/**
 * 把帧装配的观测点接到一条 {@link AudioLog} 上：HS / WY 共用的唯一实现。
 *
 * <p>产品侧只提供 {@code tag + AudioLog + 字节序}，不再各写一份 trace。</p>
 *
 * <h3>定位：只报“诊断事实”，不打印业务数据</h3>
 * <p>真正的原始数据流（SPP 控制/音频字节、PCM 解码字节）由 <b>Biz 层在处理数据时</b>打印，
 * lib 层默认<b>不</b>逐包 dump 数据（440B/包、每秒数十帧，会把日志淹没）。本类只保留低频、
 * 高价值的诊断事实：</p>
 * <table>
 *   <tr><th>场景</th><th>级别</th><th>内容</th></tr>
 *   <tr><td>格式锁定（首包，一次性）</td><td>INFO</td><td>完整几何 + 字节序</td></tr>
 *   <tr><td>零帧（疑似切帧失败）</td><td>WARN</td><td>长度 + 首序号 + 头部字节（故障定位）</td></tr>
 *   <tr><td>序号缺口</td><td>WARN</td><td>缺口折算</td></tr>
 *   <tr><td>无法识别格式</td><td>WARN</td><td>长度 + 暂存</td></tr>
 * </table>
 */
public final class LoggingAudioFrameTrace implements AudioFrameTrace {

    /** 零帧告警时打印的头部字节数上限。 */
    private static final int FAULT_HEAD_BYTES = 44;

    private final String tag;
    private final AudioLog log;
    private final AudioWireByteOrder byteOrder;

    /** 兼容构造：默认小端（既有产品行为）。 */
    public LoggingAudioFrameTrace(@NonNull String tag, @NonNull AudioLog log) {
        this(tag, log, AudioWireByteOrder.LITTLE_ENDIAN);
    }

    public LoggingAudioFrameTrace(@NonNull String tag,
                                  @NonNull AudioLog log,
                                  @NonNull AudioWireByteOrder byteOrder) {
        this.tag = tag;
        this.log = log;
        this.byteOrder = byteOrder;
    }

    /** 消息前缀统一带 tag，产品日志门面只补自己的模块前缀。 */
    @NonNull
    private String withTag(@NonNull String message) {
        return '[' + tag + "] " + message;
    }

    @Override
    public void onFormatLocked(@NonNull AudioNotifyFormat format) {
        log.info(withTag("锁定音频 Notify 格式: " + format
                + " 布局=" + format.getLayout()
                + " 单帧Opus=" + format.getEncodedOpusBytes() + "B"
                + " 每包帧数=" + format.framesCoveredByNotify()
                + " 包长=" + format.getNotifyBytes() + "B"
                + " 字节序=" + byteOrder));
    }

    @Override
    public void onFormatUnresolved(int chunkBytes, int pendingBytes) {
        log.warn(withTag("无法识别音频 Notify 格式，当前暂存 size=" + pendingBytes
                + " chunkLen=" + chunkBytes));
    }

    @Override
    public void onDataIndexGap(int fromIndex, int toIndex, int framesPerNotify) {
        int missing = toIndex - fromIndex - framesPerNotify;
        if (missing <= 0) {
            // 正常几何：序号按帧递增，差 == 每包帧数不是缺口。
            return;
        }
        log.warn(withTag("dataIndex 前向跳号: " + fromIndex + " -> " + toIndex
                + "（每包帧数=" + framesPerNotify + "，实际缺帧=" + missing + "）"));
    }

    @Override
    public void onChunkProcessed(@NonNull byte[] chunk, int framesProduced, int firstIndex) {
        // 正常切片不打印数据（那是 Biz 的职责）。只有「有输入却解不出帧」才告警，便于定位切帧失败。
        if (framesProduced <= 0) {
            log.warn(withTag("切片未产出帧 chunkLen=" + chunk.length
                    + " firstIndex=" + firstIndex + " head=" + hexHead(chunk)));
        }
    }

    /** 前 {@value #FAULT_HEAD_BYTES} 字节的十六进制，仅用于故障定位。 */
    @NonNull
    private static String hexHead(@NonNull byte[] data) {
        int count = Math.min(FAULT_HEAD_BYTES, data.length);
        StringBuilder sb = new StringBuilder(count * 3);
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(String.format(Locale.ROOT, "%02X", data[i] & 0xFF));
        }
        if (data.length > count) {
            sb.append(" …(").append(data.length).append("B)");
        }
        return sb.toString();
    }
}
