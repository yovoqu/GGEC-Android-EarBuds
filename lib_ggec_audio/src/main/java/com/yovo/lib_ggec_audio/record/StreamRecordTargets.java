package com.yovo.lib_ggec_audio.record;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;

/**
 * 随录随传落盘目标解析：自定义文件名与目录规则的唯一实现。
 *
 * <p>默认文件名（时间戳）由产品路径解析器给出，本类只负责「有自定义名时用自定义名、
 * 没有则用产品默认文件」这一条规则。</p>
 */
public final class StreamRecordTargets {

    private static final String PCM_SUFFIX = ".pcm";

    private StreamRecordTargets() {
    }

    /**
     * 解析本次录制的目标文件。
     *
     * @param config 录制配置
     * @param customSaveDir 自定义目录为 {@code null} 时使用的产品默认流目录
     * @param defaultStreamFile 产品默认时间戳文件（无自定义文件名时使用）
     */
    @NonNull
    public static File resolve(@NonNull StreamRecordConfig config,
                               @NonNull File customSaveDir,
                               @NonNull File defaultStreamFile) {
        String customName = config.customFileName();
        if (customName == null || customName.isEmpty()) {
            return defaultStreamFile;
        }
        File parent = config.customSaveDir() != null ? config.customSaveDir() : customSaveDir;
        String name = customName.contains(".") ? customName : customName + PCM_SUFFIX;
        return new File(parent, name);
    }

    /** 目标文件存在且非空才算落地成功。 */
    public static boolean hasContent(@Nullable File file) {
        return file != null && file.exists() && file.length() > 0;
    }
}
