package com.yovo.lib_ggec_opus.internal.writer;

import androidx.annotation.NonNull;

import com.yovo.lib_ggec_opus.api.YoPcmWriter;

import java.io.File;
import java.util.Objects;

/**
 * PCM 写入器构造入口（本包唯一的对外类型）。
 *
 * <p>实现类 {@link YoPcmWriterImpl} 是包内可见：模块外只能通过门面
 * {@code YoOpusSdk} 或本类拿到 {@link YoPcmWriter} 契约。</p>
 */
public final class WriterFactory {

    private WriterFactory() {
    }

    @NonNull
    public static YoPcmWriter open(@NonNull File targetFile) {
        return new YoPcmWriterImpl(Objects.requireNonNull(targetFile, "targetFile"));
    }
}
