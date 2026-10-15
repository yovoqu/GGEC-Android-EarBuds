package com.yovo.lib_bluetooth.internal.common.permission;

import android.content.Context;

import androidx.annotation.NonNull;

/**
 * 蓝牙权限端口：由 provider/lib_common 实现并委托 YoPermission，
 * 业务层不直接依赖 lib_common。
 */
public interface BluetoothPermissionPort {

    BluetoothPermissionPort UNINSTALLED = (context, permission) -> {
        throw new IllegalStateException(
                "BluetoothPermissionPort 未安装。必须通过 YoBluetoothSDK 创建客户端。");
    };

    /**
     * 检查指定蓝牙权限当前是否已授予。
     */
    boolean has(@NonNull Context context, @NonNull YoBluetoothPermission permission);
}
