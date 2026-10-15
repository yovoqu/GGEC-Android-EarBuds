package com.yovo.lib_common.system.file.internal.resolve;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_common.system.file.model.PathTarget;

import java.io.File;

/**
 * PathTarget 根目录提供者（平台实现与测试实现可注入）。
 */
public interface PathRootProvider {

    /**
     * 返回目标根目录；外部存储不可用时返回 null。
     */
    @Nullable
    File root(@NonNull PathTarget target);
}
