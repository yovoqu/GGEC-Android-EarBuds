package com.yovo.lib_common.system.file.model;

import android.content.Context;
import android.net.Uri;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * SAF 目录树选择端口：唯一通路是 Activity Result API 注册选择器 + Uri 结果落库。
 */
public interface SafTreePicker {

    /**
     * 注册 Activity Result API 选择器。
     */
    @NonNull
    ActivityResultLauncher<Uri> register(@NonNull ComponentActivity activity,
                                         @NonNull PickedCallback callback);

    /** 授权/取消回调。 */
    interface PickedCallback {
        void onResult(boolean granted);
    }

    /** 处理选择结果；取消时记录 dismissed 并返回 false。 */
    boolean handlePicked(@NonNull Context context, @Nullable Uri treeUri);
}
