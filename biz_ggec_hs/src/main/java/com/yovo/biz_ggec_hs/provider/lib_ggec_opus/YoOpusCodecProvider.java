package com.yovo.biz_ggec_hs.provider.lib_ggec_opus;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ggec_audio.record.AudioCodecProviderPort;
import com.yovo.lib_ggec_audio.record.AudioDecoder;
import com.yovo.lib_ggec_audio.record.AudioFileWriter;
import com.yovo.lib_ggec_opus.YoOpusSdk;
import com.yovo.lib_ggec_opus.api.YoOpusDecoder;
import com.yovo.lib_ggec_opus.api.YoPcmWriter;

import java.io.File;
import java.util.Objects;

/**
 * 把 {@code lib_ggec_opus} 的音频编解码与落盘能力适配为共享录音端口
 * {@link AudioCodecProviderPort}。
 *
 * <p>本类是 {@code biz_ggec_hs} 内部唯一允许 {@code import com.yovo.lib_ggec_opus} 的地方。</p>
 */
public final class YoOpusCodecProvider implements AudioCodecProviderPort {

    @NonNull
    @Override
    public AudioDecoder createDecoder() {
        YoOpusDecoder delegate = YoOpusSdk.openDecoder();
        return new AudioDecoder() {
            @Nullable
            @Override
            public byte[] decode(@NonNull byte[] opusPacket) {
                return delegate.decode(opusPacket);
            }

            @Override
            public void release() {
                delegate.release();
            }
        };
    }

    @NonNull
    @Override
    public AudioFileWriter createPcmFileWriter(@NonNull File targetFile) {
        YoPcmWriter delegate = YoOpusSdk.openPcmWriter(Objects.requireNonNull(targetFile, "targetFile"));
        return new AudioFileWriter() {
            @Override
            public void writePcm(@NonNull byte[] pcmData) {
                delegate.writePcm(pcmData);
            }

            @Override
            public void close() {
                delegate.close();
            }

            @NonNull
            @Override
            public File getFile() {
                return delegate.getFile();
            }

            @Override
            public long getDurationMs() {
                return delegate.getDurationMs();
            }

            @Override
            public long getFileSizeBytes() {
                return delegate.getFileSizeBytes();
            }
        };
    }
}
