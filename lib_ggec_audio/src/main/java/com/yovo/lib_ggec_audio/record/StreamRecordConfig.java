package com.yovo.lib_ggec_audio.record;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.Objects;

/**
 * 随录随传配置契约：字段与语义的唯一实现位置。
 *
 * <p>产品模块只在自己的 {@code api} 包里保留一层「产品构造表」（静态工厂），
 * 数据形状、模式语义与文件解析都在这里，不再各写一份 record。</p>
 */
public class StreamRecordConfig {

    /** 输出模式。 */
    public enum RecordOutputMode {
        /** 仅音频流：实时把解码后的 PCM 分发给上层，不落盘写文件。 */
        STREAM_ONLY,
        /** 仅文件：把解码后的 PCM 写入文件，不分发实时 PCM。 */
        FILE_ONLY,
        /** 流 + 文件：同时分发实时 PCM 并写盘。 */
        BOTH
    }

    @NonNull
    private final RecordOutputMode outputMode;
    @Nullable
    private final String customFileName;
    @Nullable
    private final File customSaveDir;

    /**
     * 允许产品模块在自己的 {@code api} 包里定义同形子类，用静态工厂暴露产品级常量。
     */
    protected StreamRecordConfig(@NonNull RecordOutputMode outputMode,
                                 @Nullable String customFileName,
                                 @Nullable File customSaveDir) {
        this.outputMode = Objects.requireNonNull(outputMode, "outputMode");
        this.customFileName = customFileName;
        this.customSaveDir = customSaveDir;
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

    @NonNull
    public RecordOutputMode outputMode() {
        return outputMode;
    }

    @Nullable
    public String customFileName() {
        return customFileName;
    }

    @Nullable
    public File customSaveDir() {
        return customSaveDir;
    }
}
