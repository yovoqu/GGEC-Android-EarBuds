package com.yovo.lib_common.system.permission.internal.android;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.app.NotificationManagerCompat;

import com.yovo.lib_common.system.permission.model.Permission;
import com.yovo.lib_common.system.permission.internal.PermissionChecker;
import com.yovo.lib_common.system.permission.model.PermissionStatus;

/**
 * Android 权限检查实现（适配 Android 10 ~ Android 17）。
 */
public final class AndroidPermissionChecker implements PermissionChecker {

    @NonNull
    @Override
    public PermissionStatus check(@NonNull Context context, @NonNull Permission permission) {
        if (!permission.isRequired(Build.VERSION.SDK_INT)) {
            return PermissionStatus.NOT_REQUIRED;
        }
        if (permission.isInstallTime()) {
            boolean granted = ContextCompat.checkSelfPermission(
                    context, permission.androidPermission())
                    == PackageManager.PERMISSION_GRANTED;
            return granted ? PermissionStatus.GRANTED : PermissionStatus.DENIED;
        }
        if (permission.isRuntime()) {
            boolean granted = ContextCompat.checkSelfPermission(
                    context, permission.androidPermission())
                    == PackageManager.PERMISSION_GRANTED;
            return granted ? PermissionStatus.GRANTED : PermissionStatus.DENIED;
        }
        if (permission == Permission.MANAGE_EXTERNAL_STORAGE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                return Environment.isExternalStorageManager()
                        ? PermissionStatus.GRANTED
                        : PermissionStatus.DENIED;
            }
            return PermissionStatus.DENIED;
        }
        if (permission == Permission.REQUEST_INSTALL_PACKAGES) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                return context.getPackageManager().canRequestPackageInstalls()
                        ? PermissionStatus.GRANTED
                        : PermissionStatus.DENIED;
            }
            return PermissionStatus.DENIED;
        }
        if (permission == Permission.SYSTEM_ALERT_WINDOW) {
            return Settings.canDrawOverlays(context)
                    ? PermissionStatus.GRANTED
                    : PermissionStatus.DENIED;
        }
        if (permission == Permission.WRITE_SETTINGS) {
            return Settings.System.canWrite(context)
                    ? PermissionStatus.GRANTED
                    : PermissionStatus.DENIED;
        }
        if (permission == Permission.BIND_NOTIFICATION_LISTENER_SERVICE) {
            return NotificationManagerCompat.getEnabledListenerPackages(context)
                    .contains(context.getPackageName())
                    ? PermissionStatus.GRANTED
                    : PermissionStatus.DENIED;
        }
        return PermissionStatus.DENIED;
    }
}
