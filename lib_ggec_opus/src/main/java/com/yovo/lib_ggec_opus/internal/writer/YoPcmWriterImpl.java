package com.yovo.lib_ggec_opus.internal.writer;

import androidx.annotation.NonNull;

import com.yovo.lib_ggec_opus.api.YoPcmWriter;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * 原始 PCM 文件落盘写入器实现。
 */
public final class YoPcmWriterImpl implements YoPcmWriter {

    private final File file;
    private BufferedOutputStream outputStream;
    private long outputBytes = 0;
    private boolean opened = false;

    public YoPcmWriterImpl(@NonNull File targetFile) {
        this.file = targetFile;
        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try {
            outputStream = new BufferedOutputStream(new FileOutputStream(targetFile));
            outputBytes = 0;
            opened = true;
        } catch (IOException e) {
            opened = false;
        }
    }

    @Override
    public synchronized void writePcm(@NonNull byte[] pcmData) {
        if (!opened || outputStream == null || pcmData.length == 0) {
            return;
        }
        try {
            outputStream.write(pcmData);
            outputBytes += pcmData.length;
        } catch (IOException ignored) {
        }
    }

    @Override
    public synchronized void close() {
        if (outputStream != null) {
            try {
                outputStream.flush();
                outputStream.close();
            } catch (IOException ignored) {
            }
            outputStream = null;
        }
        opened = false;
    }

    @NonNull
    @Override
    public File getFile() {
        return file;
    }

    @Override
    public long getDurationMs() {
        return outputBytes / 32L;
    }

    @Override
    public long getFileSizeBytes() {
        return file.exists() ? file.length() : outputBytes;
    }
}
