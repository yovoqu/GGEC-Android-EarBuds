package com.yovo.lib_bluetooth.provider.lib_common.permission;

import android.content.Context;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.common.permission.BluetoothPermissionPort;
import com.yovo.lib_bluetooth.internal.common.permission.YoBluetoothPermission;
import com.yovo.lib_common.system.permission.YoPermission;
import com.yovo.lib_common.system.permission.model.Permission;
import com.yovo.lib_common.system.permission.model.PermissionPolicy;

/**
 * lib_common YoPermission 的蓝牙权限适配：本模块唯一允许 import {@code YoPermission} 的类。
 *
 * <p>由装配根 {@code BluetoothSdkCore} 构造，注入 {@code YoBluetoothPermissionChecker}
 * 后沿客户端实现向下传；本类不持有静态实例，也不提供任何安装入口。</p>
 */
public final class BluetoothPermissionProvider implements BluetoothPermissionPort {

    public BluetoothPermissionProvider() {
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
