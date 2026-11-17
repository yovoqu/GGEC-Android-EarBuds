package com.yovo.biz_ggec_wy.internal.center.port;

import androidx.annotation.NonNull;

import java.io.File;

/**
 * 录音落盘文件写入器端口。
 */
public interface WyAudioFileWriter {

    void writePcm(@NonNull byte[] pcmData);

    void close();

    @NonNull
    File getFile();

    long getDurationMs();

    long getFileSizeBytes();
}
