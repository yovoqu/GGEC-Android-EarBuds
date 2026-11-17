package com.yovo.lib_ggec_opus;

import androidx.annotation.NonNull;

import com.yovo.lib_ggec_opus.api.YoOpusDecoder;
import com.yovo.lib_ggec_opus.api.YoPcmWriter;
import com.yovo.lib_ggec_opus.internal.codec.CodecFactory;
import com.yovo.lib_ggec_opus.internal.writer.WriterFactory;

import java.io.File;
import java.util.Objects;

/**
 * lib_ggec_opus 唯一门面入口工厂。
 *
 * <p>模块公开面只有本类与 {@code api/**}：实现类都在 {@code internal/**} 里包内可见，
 * 第三方 Opus SDK 在 {@code src/vendored/java}（包名 {@code com.bes.opus}）且禁止外引。</p>
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
        return CodecFactory.openDecoder();
    }

    /**
     * 创建一个高效率 PCM 文件落盘写入器。
     *
     * <p>打开失败不会抛异常，而是反映到 {@link YoPcmWriter#status()} 的
     * {@code OPEN_FAILED} 分类；录制结束时请检查一次状态再确认文件可用。</p>
     *
     * @param targetFile 目标落盘文件
     */
    @NonNull
    public static YoPcmWriter openPcmWriter(@NonNull File targetFile) {
        return WriterFactory.open(Objects.requireNonNull(targetFile, "targetFile"));
    }
}
