package com.yovo.lib_ggec_audio.record;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 录音音频解码端口：把单帧 20ms Opus 数据包解码为 16kHz / 16-bit 单声道 PCM。
 *
 * <p>HS 与 WY 共用这一份契约：解码器生命周期由 {@link StreamRecordPipeline} 统一管理，
 * 产品只提供工厂（{@link AudioCodecProviderPort}）。</p>
 */
public interface AudioDecoder {

    /**
     * 解码单帧 Opus 数据包。
     *
     * @param opusPacket Opus 帧字节数组
     * @return PCM 字节数组；解码失败返回 {@code null}
     */
    @Nullable
    byte[] decode(@NonNull byte[] opusPacket);

    /** 释放解码器实例及 native 句柄。 */
    void release();
}
