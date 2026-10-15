package com.yovo.lib_common.system.file.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;

/**
 * SAF 目录树中的单个文件（DocumentFile 抽象，不向调用方暴露实现类型）。
 */
public interface SafFile {

    @NonNull
    String name();

    long lastModified();

    long length();

    /** 是否为目录条目。 */
    boolean isDirectory();

    /**
     * 复制到本地目标文件；成功返回 target，失败返回 null（并清理半成品）。
     */
    @Nullable
    File copyTo(@NonNull File target);

    boolean delete();
}
