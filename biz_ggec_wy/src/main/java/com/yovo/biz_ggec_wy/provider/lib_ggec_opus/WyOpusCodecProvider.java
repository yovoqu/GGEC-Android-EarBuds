package com.yovo.biz_ggec_wy.provider.lib_ggec_opus;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_ggec_wy.internal.center.port.WyAudioCodecProviderPort;
import com.yovo.biz_ggec_wy.internal.center.port.WyAudioDecoder;
import com.yovo.biz_ggec_wy.internal.center.port.WyAudioFileWriter;
import com.yovo.lib_ggec_opus.YoOpusSdk;
import com.yovo.lib_ggec_opus.api.YoOpusDecoder;
import com.yovo.lib_ggec_opus.api.YoPcmWriter;

import java.io.File;
import java.util.Objects;

/**
 * 把 {@code lib_ggec_opus} 的音频编解码与落盘能力适配为模块内部端口 {@link WyAudioCodecProviderPort}。
 */
public final class WyOpusCodecProvider implements WyAudioCodecProviderPort {

    @NonNull
    @Override
    public WyAudioDecoder createDecoder() {
        YoOpusDecoder delegate = YoOpusSdk.openDecoder();
        return new WyAudioDecoder() {
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
    public WyAudioFileWriter createPcmFileWriter(@NonNull File targetFile) {
        YoPcmWriter delegate = YoOpusSdk.openPcmWriter(Objects.requireNonNull(targetFile, "targetFile"));
        return new WyAudioFileWriter() {
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
