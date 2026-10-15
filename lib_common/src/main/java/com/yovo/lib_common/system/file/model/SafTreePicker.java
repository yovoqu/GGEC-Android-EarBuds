package com.yovo.lib_common.system.file.model;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * SAF 目录树选择端口：拉起系统选择器并持久化授权。
 */
public interface SafTreePicker {

    /**
     * 注册 Activity Result API 选择器（推荐入口）。
     */
    @NonNull
    ActivityResultLauncher<Uri> register(@NonNull ComponentActivity activity,
                                         @NonNull PickedCallback callback);

    /** 授权/取消回调。 */
    interface PickedCallback {
        void onResult(boolean granted);
    }

    /** 兼容旧式 startActivityForResult 宿主（已废弃，建议使用 Activity Result API）。 */
    @Deprecated
    void pick(@NonNull Activity activity);

    /** 处理选择结果；取消时记录 dismissed 并返回 false。 */
    boolean handlePicked(@NonNull Context context, @Nullable Uri treeUri);

    /** 兼容旧式 onActivityResult 的 Intent 结果。 */
    boolean handlePicked(@NonNull Context context, @Nullable Intent data);
}
