package com.yovo.lib_common.system.file.model;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.List;
import java.util.function.Predicate;

/**
 * SAF 目录树访问抽象：列出、查找、创建、删除与复制文件。
 */
public interface SafDirectory {

    @NonNull
    Uri uri();

    @NonNull
    List<SafFile> listFiles();

    @NonNull
    List<SafFile> listFiles(@NonNull Predicate<String> nameFilter);

    @Nullable
    SafFile findFile(@NonNull String name);

    @Nullable
    SafFile createFile(@NonNull String name, @NonNull String mimeType);

    boolean deleteFile(@NonNull String name);

    /**
     * 将本地文件复制到目录树；成功返回 SafFile，失败返回 null（并清理半成品）。
     */
    @Nullable
    SafFile copyFrom(@NonNull File source, @NonNull String name, @NonNull String mimeType);
}
