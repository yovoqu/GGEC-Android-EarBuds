package com.yovo.lib_ggec_opus.internal.writer;

import androidx.annotation.NonNull;

import com.yovo.lib_ggec_opus.api.PcmWriterStatus;
import com.yovo.lib_ggec_opus.api.YoOpusAudioContract;
import com.yovo.lib_ggec_opus.api.YoPcmWriter;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * 原始 PCM 文件落盘写入器实现（包内可见：对外只经 {@link WriterFactory} 构造）。
 *
 * <p>失败语义：构造期打开失败、写入失败、flush/close 失败各自归类到
 * {@link PcmWriterStatus}，首次失败后状态粘滞、后续写入直接跳过；
 * 不存在空 catch，也不需要调用方自己猜「文件为什么是空的」。</p>
 */
final class YoPcmWriterImpl implements YoPcmWriter {

    @NonNull private final File file;
    private BufferedOutputStream outputStream;
    private long outputBytes = 0;
    @NonNull private volatile PcmWriterStatus status = PcmWriterStatus.open();

    YoPcmWriterImpl(@NonNull File targetFile) {
        this.file = targetFile;
        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            // 目录建不出来就不再尝试打开流：分类为打开失败，避免「写一半才知道」。
            this.status = PcmWriterStatus.openFailed(new IOException(
                    "cannot create parent directory: " + parent.getAbsolutePath()));
            return;
        }
        try {
            outputStream = new BufferedOutputStream(new FileOutputStream(targetFile));
        } catch (IOException openFailure) {
            outputStream = null;
            status = PcmWriterStatus.openFailed(openFailure);
        }
    }

    @Override
    public synchronized void writePcm(@NonNull byte[] pcmData) {
        if (!status.isWritable() || outputStream == null || pcmData.length == 0) {
            return;
        }
        try {
            outputStream.write(pcmData);
            outputBytes += pcmData.length;
        } catch (IOException writeFailure) {
            status = PcmWriterStatus.writeFailed(writeFailure);
        }
    }

    @Override
    public synchronized void close() {
        if (outputStream == null) {
            if (!status.isFailure()) {
                status = PcmWriterStatus.closed();
            }
            return;
        }
        BufferedOutputStream closing = outputStream;
        outputStream = null;
        try {
            closing.flush();
            closing.close();
            if (!status.isFailure()) {
                status = PcmWriterStatus.closed();
            }
        } catch (IOException closeFailure) {
            status = PcmWriterStatus.closeFailed(closeFailure);
        }
    }

    @NonNull
    @Override
    public PcmWriterStatus status() {
        return status;
    }

    @NonNull
    @Override
    public File getFile() {
        return file;
    }

    @Override
    public long getDurationMs() {
        return YoOpusAudioContract.durationMsFor(outputBytes);
    }

    @Override
    public long getFileSizeBytes() {
        // 已写入字节数是权威值：BufferedOutputStream 未 flush 前 file.length() 仍为 0，
        // 用文件长度会把「已写入但未落盘」误报成 0。
        return outputBytes;
    }
}
