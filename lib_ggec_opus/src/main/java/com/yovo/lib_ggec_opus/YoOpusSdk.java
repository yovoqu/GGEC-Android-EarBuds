package com.yovo.lib_ggec_opus;

import androidx.annotation.NonNull;

import com.yovo.lib_ggec_opus.api.YoOpusDecoder;
import com.yovo.lib_ggec_opus.api.YoPcmWriter;
import com.yovo.lib_ggec_opus.internal.codec.YoOpusDecoderImpl;
import com.yovo.lib_ggec_opus.internal.writer.YoPcmWriterImpl;

import java.io.File;
import java.util.Objects;

/**
 * lib_ggec_opus 唯一门面入口工厂。
 */
public final class YoOpusSdk {

    private static final String VERSION = "1.0.0";

    private YoOpusSdk() {
    }

    @NonNull
    public static String getVersion() {
        return VERSION;
    }

    /**
     * 创建一个纯净的 Opus 帧解码器。
     */
    @NonNull
    public static YoOpusDecoder openDecoder() {
        return new YoOpusDecoderImpl();
    }

    /**
     * 创建一个高效率 PCM 文件落盘写入器。
     *
     * @param targetFile 目标落盘文件
     */
    @NonNull
    public static YoPcmWriter openPcmWriter(@NonNull File targetFile) {
        return new YoPcmWriterImpl(Objects.requireNonNull(targetFile, "targetFile"));
    }
}
