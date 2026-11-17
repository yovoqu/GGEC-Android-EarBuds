package com.yovo.biz_ggec_wy.internal.center.port;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 录音音频解码端口：将单帧 20ms Opus 数据包解码为 16kHz / 16-bit 单声道 PCM 数据。
 */
public interface WyAudioDecoder {

    /**
     * 解码单帧 Opus 数据包。
     *
     * @param opusPacket Opus 帧字节数组
     * @return 640 字节 PCM 数据，失败返回 null
     */
    @Nullable
    byte[] decode(@NonNull byte[] opusPacket);

    /**
     * 释放解码器实例及 native 句柄。
     */
    void release();
}
