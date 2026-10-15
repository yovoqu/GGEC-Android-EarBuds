package com.yovo.lib_common.system.permission.internal.android;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.yovo.lib_common.system.permission.model.Permission;
import com.yovo.lib_common.system.permission.internal.PermissionRequester;

import java.util.List;

/**
 * Android 权限请求实现：运行时权限弹窗 + “所有文件访问”设置页。
 */
public final class AndroidPermissionRequester implements PermissionRequester {

    @Override
    public void requestRuntime(@NonNull Activity activity,
                               @NonNull List<Permission> permissions,
                               int requestCode) {
        String[] names = new String[permissions.size()];
        for (int i = 0; i < permissions.size(); i++) {
            names[i] = permissions.get(i).androidPermission();
        }
        ActivityCompat.requestPermissions(activity, names, requestCode);
    }

    @Override
    public boolean requestSpecialAccess(@NonNull Activity activity,
                                        @NonNull Permission permission) {
        if (permission == Permission.MANAGE_EXTERNAL_STORAGE) {
            return startManageExternalStorage(activity);
        }
        if (permission == Permission.REQUEST_INSTALL_PACKAGES) {
            return start(activity, Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
        }
        if (permission == Permission.SYSTEM_ALERT_WINDOW) {
            return start(activity, Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
        }
        if (permission == Permission.WRITE_SETTINGS) {
            return start(activity, Settings.ACTION_MANAGE_WRITE_SETTINGS);
        }
        if (permission == Permission.BIND_NOTIFICATION_LISTENER_SERVICE) {
            return start(activity, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        }
        return false;
    }

    @Override
    public void openAppSettings(@NonNull Activity activity) {
        try {
            Intent intent = new Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            try {
                activity.startActivity(new Intent(Settings.ACTION_APPLICATION_SETTINGS));
            } catch (ActivityNotFoundException ignored) {
                // 设备无设置入口时由调用方提示。
            }
        }
    }

    private static boolean startManageExternalStorage(@NonNull Activity activity) {
        try {
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException e) {
            try {
                activity.startActivity(
                        new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                return true;
            } catch (ActivityNotFoundException ignored) {
                // 设备不支持时由调用方决定降级策略。
                return false;
            }
        }
    }

    private static boolean start(@NonNull Activity activity, @NonNull String action) {
        try {
            Intent intent = new Intent(action);
            intent.setData(Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException e) {
            try {
                activity.startActivity(new Intent(action));
                return true;
            } catch (ActivityNotFoundException ignored) {
                return false;
            }
        }
    }
}
