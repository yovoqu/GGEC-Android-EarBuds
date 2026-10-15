package com.yovo.lib_common.system.permission;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_common.system.permission.internal.core.PermissionCore;
import com.yovo.lib_common.system.permission.model.Permission;
import com.yovo.lib_common.system.permission.model.PermissionPolicy;
import com.yovo.lib_common.system.permission.model.PermissionResult;
import com.yovo.lib_common.system.permission.model.PermissionStatus;

import android.os.Build;

import java.util.List;

/**
 * 权限域唯一对外静态门面。
 */
public final class YoPermission {

    /** 默认运行时权限组请求码。 */
    public static final int REQUEST_CODE_GROUP = 0xB0F0;

    private YoPermission() {
    }

    @NonNull
    public static PermissionStatus check(@NonNull Context context,
                                         @NonNull Permission permission) {
        return PermissionCore.with(context).check(permission);
    }

    public static boolean isGranted(@NonNull Context context,
                                    @NonNull Permission permission) {
        return PermissionCore.with(context).isGranted(permission);
    }

    public static boolean isGranted(@NonNull Context context,
                                    @NonNull List<Permission> permissions) {
        return PermissionCore.with(context).isGranted(permissions);
    }

    public static boolean isGranted(@NonNull Context context,
                                    @NonNull PermissionPolicy policy) {
        return isGranted(context, policy.requiredFor(Build.VERSION.SDK_INT));
    }

    public static boolean shouldShowRationale(@NonNull Activity activity,
                                              @NonNull Permission permission) {
        return PermissionCore.with(activity).shouldShowRationale(activity, permission);
    }

    public static boolean requestIfNeeded(@NonNull Activity activity,
                                          @NonNull Permission permission) {
        return PermissionCore.with(activity).requestIfNeeded(activity, permission);
    }

    public static boolean requestIfNeeded(@NonNull Activity activity,
                                          @NonNull Permission permission,
                                          int requestCode) {
        return PermissionCore.with(activity).requestIfNeeded(activity, permission, requestCode);
    }

    public static boolean requestIfNeeded(@NonNull Activity activity,
                                          @NonNull List<Permission> permissions) {
        return PermissionCore.with(activity).requestIfNeeded(activity, permissions);
    }

    public static boolean requestIfNeeded(@NonNull Activity activity,
                                          @NonNull List<Permission> permissions,
                                          int requestCode) {
        return PermissionCore.with(activity).requestIfNeeded(activity, permissions, requestCode);
    }

    public static void requestSpecialAccess(@NonNull Activity activity,
                                            @NonNull List<Permission> permissions,
                                            @NonNull SpecialAccessCallback callback) {
        PermissionCore.with(activity).requestSpecialAccess(
                activity, permissions, new PermissionCore.SpecialAccessCallback() {
                    @Override
                    public void onResult(boolean allGranted,
                                         @NonNull List<Permission> denied) {
                        callback.onResult(allGranted, denied);
                    }
                });
    }

    public static void openAppSettings(@NonNull Activity activity) {
        PermissionCore.with(activity).openAppSettings(activity);
    }

    @NonNull
    public static PermissionResult handleRequestPermissionsResult(
            int requestCode,
            @Nullable String[] permissions,
            @Nullable int[] grantResults) {
        PermissionCore core = PermissionCore.peek();
        if (core == null) {
            return PermissionResult.ignored();
        }
        return core.handleRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @NonNull
    public static PermissionResult handleRequestPermissionsResult(
            @Nullable Activity activity,
            int requestCode,
            @Nullable String[] permissions,
            @Nullable int[] grantResults) {
        if (activity != null) {
            return PermissionCore.with(activity).handleRequestPermissionsResult(
                    activity, requestCode, permissions, grantResults);
        }
        PermissionCore core = PermissionCore.peek();
        if (core == null) {
            return PermissionResult.ignored();
        }
        return core.handleRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    public interface SpecialAccessCallback {
        void onResult(boolean allGranted, @NonNull List<Permission> denied);
    }
}
