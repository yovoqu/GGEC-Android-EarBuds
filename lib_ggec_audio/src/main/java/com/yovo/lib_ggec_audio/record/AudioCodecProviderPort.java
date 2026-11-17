package com.yovo.lib_ggec_audio.record;

import androidx.annotation.NonNull;

import java.io.File;

/**
 * 录音音频编解码与落盘提供方端口。
 *
 * <p>产品侧只实现这一个工厂（把 {@code lib_ggec_opus} 适配进来），
 * 管线如何使用解码器与写入器由 {@link StreamRecordPipeline} 统一决定。</p>
 */
public interface AudioCodecProviderPort {

    @NonNull
    AudioDecoder createDecoder();

    @NonNull
    AudioFileWriter createPcmFileWriter(@NonNull File targetFile);
}
