package com.yovo.lib_ggec_audio.record;

import androidx.annotation.NonNull;

import com.yovo.lib_ggec_audio.AudioFrameTrace;
import com.yovo.lib_ggec_audio.AudioNotifyFormat;

/**
 * 把帧装配的观测点接到一条 {@link AudioLog} 上：HS / WY 共用的唯一实现。
 *
 * <p>产品侧只提供 {@code tag + AudioLog}，不再各写一份 40 行的 trace。</p>
 */
public final class LoggingAudioFrameTrace implements AudioFrameTrace {

    private final String tag;
    private final AudioLog log;

    public LoggingAudioFrameTrace(@NonNull String tag, @NonNull AudioLog log) {
        this.tag = tag;
        this.log = log;
    }

    /** 消息前缀统一带 tag，产品日志门面只补自己的模块前缀。 */
    @NonNull
    private String withTag(@NonNull String message) {
        return '[' + tag + "] " + message;
    }

    @Override
    public void onFormatLocked(@NonNull AudioNotifyFormat format) {
        log.info(withTag("锁定音频 Notify 格式: " + format
                + " 单帧Opus=" + format.getEncodedOpusBytes() + "B"));
    }

    @Override
    public void onFormatUnresolved(int chunkBytes, int pendingBytes) {
        log.warn(withTag("无法识别音频 Notify 格式，当前暂存 size=" + pendingBytes
                + " chunkLen=" + chunkBytes));
    }

    @Override
    public void onDataIndexGap(int fromIndex, int toIndex) {
        log.warn(withTag("dataIndex 前向跳号: " + fromIndex + " -> " + toIndex
                + "（缺口=" + (toIndex - fromIndex - 1) + "）"));
    }
}
