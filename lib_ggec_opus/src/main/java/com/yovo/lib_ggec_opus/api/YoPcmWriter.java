package com.yovo.lib_ggec_opus.api;

import androidx.annotation.NonNull;

import java.io.File;

/**
 * PCM 文件写入器契约。
 */
public interface YoPcmWriter {

    /**
     * 写入 PCM 字节数据。
     *
     * @param pcmData 16kHz / 16bit / mono PCM 字节
     */
    void writePcm(@NonNull byte[] pcmData);

    /**
     * 刷新并关闭文件流。
     */
    void close();

    /**
     * 获取目标文件。
     */
    @NonNull
    File getFile();

    /**
     * 获取录制时长（毫秒）。
     */
    long getDurationMs();

    /**
     * 获取文件大小（字节）。
     */
    long getFileSizeBytes();
}
