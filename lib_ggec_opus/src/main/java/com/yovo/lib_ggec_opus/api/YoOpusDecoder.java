package com.yovo.lib_ggec_opus.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Opus 帧解码器契约。
 *
 * <p>输入 20ms Opus 帧（40B / 80B / 160B），输出 16kHz / 16-bit / 单声道 PCM（640 字节）。</p>
 */
public interface YoOpusDecoder {

    /**
     * 解码单帧 Opus 数据包。
     *
     * @param opusPacket 20ms Opus 编码切片
     * @return 解码后的 640 字节 PCM 数组，失败或已释放返回 null
     */
    @Nullable
    byte[] decode(@NonNull byte[] opusPacket);

    /**
     * 释放底层 JNI 原生解码器资源。
     */
    void release();
}
