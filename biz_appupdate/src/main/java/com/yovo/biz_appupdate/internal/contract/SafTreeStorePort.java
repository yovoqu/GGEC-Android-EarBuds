package com.yovo.biz_appupdate.internal.contract;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.List;
import java.util.function.Predicate;

/**
 * SAF 能力端口（模块中立，不暴露 lib_common 类型）。
 */
public interface SafTreeStorePort {

    @Nullable
    Uri loadTreeUri();

    boolean isDismissed();

    boolean isTreeAccessible(@Nullable Uri treeUri);

    boolean copyApkToTree(@NonNull Uri treeUri, @NonNull File source);

    @NonNull
    List<SafEntry> listEntries(@NonNull Uri treeUri,
                               @NonNull Predicate<String> nameFilter);

    boolean deleteEntry(@NonNull Uri treeUri, @NonNull String name);
}
