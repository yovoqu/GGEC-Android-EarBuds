package com.yovo.biz_ggec_wy.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;

/**
 * 随录随传配置参数。
 */
public record StreamRecordConfig(
        @NonNull RecordOutputMode outputMode,
        @Nullable String customFileName,
        @Nullable File customSaveDir
) {

    public enum RecordOutputMode {
        /** 仅音频流：实时将解码后的 PCM 分发给上层，不落盘写文件 */
        STREAM_ONLY,
        /** 仅文件：内部调用 lib_ggec_opus 编码写入文件，不分发实时 PCM */
        FILE_ONLY,
        /** 流 + 文件：同时分发实时 PCM 并编码写盘 */
        BOTH
    }

    public static StreamRecordConfig defaultFile() {
        return new StreamRecordConfig(RecordOutputMode.FILE_ONLY, null, null);
    }

    public static StreamRecordConfig fileWithCustomName(@NonNull String fileName, @Nullable File saveDir) {
        return new StreamRecordConfig(RecordOutputMode.FILE_ONLY, fileName, saveDir);
    }

    public static StreamRecordConfig streamOnly() {
        return new StreamRecordConfig(RecordOutputMode.STREAM_ONLY, null, null);
    }

    public static StreamRecordConfig both(@Nullable String fileName, @Nullable File saveDir) {
        return new StreamRecordConfig(RecordOutputMode.BOTH, fileName, saveDir);
    }
}
