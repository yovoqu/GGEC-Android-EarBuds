package com.yovo.lib_common.system.permission.internal;

import com.yovo.lib_common.system.permission.model.Permission;

import android.app.Activity;

import androidx.annotation.NonNull;

import java.util.List;

/**
 * 权限请求端口：运行时权限弹窗与特殊访问设置页统一出口。
 */
public interface PermissionRequester {

    void requestRuntime(@NonNull Activity activity,
                        @NonNull List<Permission> permissions,
                        int requestCode);

    /**
     * 拉起特殊访问设置页。
     *
     * @return true 表示设置页已成功拉起；false 表示设备不支持该入口。
     */
    boolean requestSpecialAccess(@NonNull Activity activity, @NonNull Permission permission);

    /** 打开应用详情设置页（“不再询问”后的手动授权入口）。 */
    void openAppSettings(@NonNull Activity activity);
}
