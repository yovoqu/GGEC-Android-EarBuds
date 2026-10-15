package com.yovo.lib_common.system.file.model;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * SAF 目录树授权持久化端口。
 */
public interface SafTreeStore {

    void save(@NonNull Uri treeUri);

    @Nullable
    Uri load();

    boolean has();

    /** 用户取消过选择器后置位，避免每次启动重复弹窗。 */
    void markDismissed();

    /** 用户成功授权后清除取消标记。 */
    void clearDismissed();

    boolean isDismissed();
}
