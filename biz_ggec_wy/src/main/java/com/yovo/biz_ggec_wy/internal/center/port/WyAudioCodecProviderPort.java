package com.yovo.biz_ggec_wy.internal.center.port;

import androidx.annotation.NonNull;

import java.io.File;

/**
 * 录音音频编解码与落盘提供方端口抽象。
 */
public interface WyAudioCodecProviderPort {

    @NonNull
    WyAudioDecoder createDecoder();

    @NonNull
    WyAudioFileWriter createPcmFileWriter(@NonNull File targetFile);
}
