package com.yovo.lib_bluetooth.provider.lib_common;

import android.content.Context;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.common.permission.BluetoothPermissionPort;
import com.yovo.lib_bluetooth.internal.common.permission.YoBluetoothPermission;
import com.yovo.lib_common.system.permission.YoPermission;
import com.yovo.lib_common.system.permission.model.Permission;
import com.yovo.lib_common.system.permission.model.PermissionPolicy;

/**
 * lib_common YoPermission 的蓝牙模块 Provider：全模块唯一允许 import YoPermission 的类。
 */
public final class PermissionProvider implements BluetoothPermissionPort {

    public static final PermissionProvider INSTANCE = new PermissionProvider();

    private PermissionProvider() {
    }

    @Override
    public boolean has(@NonNull Context context, @NonNull YoBluetoothPermission permission) {
        switch (permission) {
            case SCAN:
                return YoPermission.isGranted(context, PermissionPolicy.BLUETOOTH_SCAN);
            case CONNECT:
                return YoPermission.isGranted(context, PermissionPolicy.BLUETOOTH_CONNECT);
            case ADVERTISE:
                return YoPermission.isGranted(context, PermissionPolicy.BLUETOOTH_ADVERTISE);
            case LOCATION:
                return YoPermission.isGranted(context, Permission.ACCESS_FINE_LOCATION)
                        || YoPermission.isGranted(context, Permission.ACCESS_COARSE_LOCATION);
            default:
                return false;
        }
    }
}
