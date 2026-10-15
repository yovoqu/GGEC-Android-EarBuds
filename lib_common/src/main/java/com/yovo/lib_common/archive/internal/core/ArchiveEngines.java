package com.yovo.lib_common.archive.internal.core;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_common.archive.internal.engine.ArchiveEngine;
import com.yovo.lib_common.archive.internal.engine.ZipArchiveEngine;
import com.yovo.lib_common.archive.model.ArchiveFormat;

/**
 * @author tingfeng.yang
 * @date 2026-07-13
 * @description 按格式解析归档引擎。首期仅注册 ZIP。
 */
public final class ArchiveEngines {

    private ArchiveEngines() {
    }

    @Nullable
    public static ArchiveEngine forFormat(@Nullable ArchiveFormat format) {
        if (format == null) {
            return null;
        }
        if (format == ArchiveFormat.ZIP) {
            return ZipArchiveEngine.INSTANCE;
        }
        return null;
    }

    @NonNull
    public static ArchiveEngine require(@NonNull ArchiveFormat format) {
        ArchiveEngine engine = forFormat(format);
        if (engine == null) {
            throw new IllegalArgumentException("unsupported archive format: " + format);
        }
        return engine;
    }
}
