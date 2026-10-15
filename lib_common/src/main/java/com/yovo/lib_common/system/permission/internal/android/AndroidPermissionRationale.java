package com.yovo.lib_common.system.permission.internal.android;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.yovo.lib_common.system.permission.model.Permission;
import com.yovo.lib_common.system.permission.internal.PermissionRationale;

/**
 * Android 运行时权限 rationale 判定实现。
 */
public final class AndroidPermissionRationale implements PermissionRationale {

    @Override
    public boolean shouldShow(@NonNull Activity activity, @NonNull Permission permission) {
        if (!permission.isRuntime()) {
            return false;
        }
        return ActivityCompat.shouldShowRequestPermissionRationale(
                activity, permission.androidPermission());
    }
}
