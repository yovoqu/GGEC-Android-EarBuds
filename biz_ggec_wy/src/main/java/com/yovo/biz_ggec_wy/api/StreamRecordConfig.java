package com.yovo.biz_ggec_wy.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;

/**
 * WY 随录随传配置：数据形状与语义的唯一实现在 {@code lib_ggec_audio}。
 *
 * <p>本类只保留 WY 的产品构造表（静态工厂），不再复制字段与模式判定。</p>
 */
public final class StreamRecordConfig
        extends com.yovo.lib_ggec_audio.record.StreamRecordConfig {

    private StreamRecordConfig(@NonNull RecordOutputMode outputMode,
                               @Nullable String customFileName,
                               @Nullable File customSaveDir) {
        super(outputMode, customFileName, customSaveDir);
    }

    @NonNull
    public static StreamRecordConfig defaultFile() {
        return new StreamRecordConfig(RecordOutputMode.FILE_ONLY, null, null);
    }

    @NonNull
    public static StreamRecordConfig fileWithCustomName(@NonNull String fileName,
                                                        @Nullable File saveDir) {
        return new StreamRecordConfig(RecordOutputMode.FILE_ONLY, fileName, saveDir);
    }

    @NonNull
    public static StreamRecordConfig streamOnly() {
        return new StreamRecordConfig(RecordOutputMode.STREAM_ONLY, null, null);
    }

    @NonNull
    public static StreamRecordConfig both(@Nullable String fileName, @Nullable File saveDir) {
        return new StreamRecordConfig(RecordOutputMode.BOTH, fileName, saveDir);
    }
}
