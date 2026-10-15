package com.yovo.lib_bluetooth.internal.common.permission;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.EnumSet;

/**
 * 检查应用是否已授予指定蓝牙相关权限，统一委托 {@link BluetoothPermissionPort}。
 */

public final class YoBluetoothPermissionChecker {

    private static volatile BluetoothPermissionPort port = BluetoothPermissionPort.UNINSTALLED;

    public static void setPort(@NonNull BluetoothPermissionPort value) {
        port = value;
    }

    public boolean has(@NonNull Context context, @NonNull YoBluetoothPermission permission) {
        return port.has(context, permission);
    }

    public boolean hasAll(@NonNull Context context, @NonNull EnumSet<YoBluetoothPermission> permissions) {
        for (YoBluetoothPermission permission : permissions) {
            if (!has(context, permission)) return false;
        }
        return true;
    }
}
