package com.yovo.biz_appupdate.internal.infrastructure.local;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_appupdate.internal.model.DownloadDirSpec;

import java.io.File;

/**
 * 下载目录解析（纯 Java）：
 * 把 {@link DownloadDirSpec} 解析为最终文件系统目录。
 *
 * <p>模型层保持纯值对象，目录解析属于存储基础设施职责。</p>
 */
public final class DownloadDirResolver {

    private DownloadDirResolver() {
    }

    /**
     * APP_PRIVATE 优先使用外部私有目录，外部不可用时回退内部 files 目录；
     * SAF 使用应用内部缓存中转目录；ABSOLUTE 返回调用方指定目录。
     */
    @NonNull
    public static File resolve(@NonNull DownloadDirSpec spec,
                               @Nullable File externalFilesDir,
                               @NonNull File filesDir) {
        switch (spec.type()) {
            case SAF:
                return new File(filesDir, DownloadDirSpec.DEFAULT_DIR_NAME + "_saf_cache");
            case ABSOLUTE:
                return spec.absoluteDir();
            case APP_PRIVATE:
            default: {
                File base = externalFilesDir != null ? externalFilesDir : filesDir;
                return new File(base, spec.relativeName());
            }
        }
    }
}
