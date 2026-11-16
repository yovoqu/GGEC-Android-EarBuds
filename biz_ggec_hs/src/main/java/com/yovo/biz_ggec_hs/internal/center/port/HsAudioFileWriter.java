package com.yovo.biz_ggec_hs.internal.center.port;

import androidx.annotation.NonNull;

import java.io.File;

/**
 * 录音音频文件落地端口。
 */
public interface HsAudioFileWriter {

    /**
     * 写入 PCM 原始数据。
     */
    void writePcm(@NonNull byte[] pcmData);

    /**
     * 刷新并关闭输出流。
     */
    void close();

    /**
     * 获取目标文件。
     */
    @NonNull
    File getFile();

    /**
     * 获取当前录制时长（毫秒）。
     */
    long getDurationMs();

    /**
     * 获取文件大小（字节）。
     */
    long getFileSizeBytes();
}
