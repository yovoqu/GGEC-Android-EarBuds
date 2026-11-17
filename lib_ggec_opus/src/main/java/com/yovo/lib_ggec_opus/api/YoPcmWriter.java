package com.yovo.lib_ggec_opus.api;

import androidx.annotation.NonNull;

import java.io.File;

/**
 * PCM 文件写入器契约。
 *
 * <p>写入/关闭失败不会抛给调用方，而是写入 {@link #status()} 的分类结果；
 * 调用方至少要在录制结束前检查一次 {@code status().isFailure()}，
 * 否则「长度为 0 的文件」会被误当成正常录制。</p>
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
     * 当前写入器状态分类（打开/写入/关闭失败的唯一上报通道）。
     */
    @NonNull
    PcmWriterStatus status();

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
