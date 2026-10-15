package com.yovo.lib_common.system.permission.internal;

import com.yovo.lib_common.system.permission.model.Permission;
import com.yovo.lib_common.system.permission.model.PermissionStatus;

import android.content.Context;

import androidx.annotation.NonNull;

/**
 * 权限状态检查端口。
 */
public interface PermissionChecker {

    @NonNull
    PermissionStatus check(@NonNull Context context, @NonNull Permission permission);
}
