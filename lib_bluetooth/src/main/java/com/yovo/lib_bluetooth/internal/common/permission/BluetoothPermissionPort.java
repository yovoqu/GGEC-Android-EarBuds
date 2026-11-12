package com.yovo.lib_bluetooth.internal.common.permission;

import android.content.Context;

import androidx.annotation.NonNull;

/**
 * 蓝牙权限端口：由 {@code provider/lib_common/permission} 实现并委托 YoPermission，
 * 业务层不直接依赖 lib_common。
 *
 * <p>实现由装配根构造后注入 {@link YoBluetoothPermissionChecker}，端口本身不含默认实现。</p>
 */
public interface BluetoothPermissionPort {

    /**
     * 检查指定蓝牙权限当前是否已授予。
     */
    boolean has(@NonNull Context context, @NonNull YoBluetoothPermission permission);
}
