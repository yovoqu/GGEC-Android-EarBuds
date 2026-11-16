package com.yovo.biz_ggec_hs.internal.center.port;

import androidx.annotation.NonNull;

import java.io.File;

/**
 * 录音音频编解码与落盘提供方端口抽象。
 */
public interface HsAudioCodecProviderPort {

    @NonNull
    HsAudioDecoder createDecoder();

    @NonNull
    HsAudioFileWriter createPcmFileWriter(@NonNull File targetFile);
}
