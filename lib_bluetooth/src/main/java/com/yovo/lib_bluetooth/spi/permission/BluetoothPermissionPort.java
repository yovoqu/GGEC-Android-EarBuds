package com.yovo.lib_bluetooth.spi.permission;

import android.content.Context;

import androidx.annotation.NonNull;

/**
 * 蓝牙权限端口（SPI）：由 {@code provider/lib_common/permission} 实现并委托 lib_common 的权限域。
 *
 * <p>端口在 {@code spi/} 而不在 {@code internal/}：提供方只允许依赖 SPI 与依赖模块的公开 API，
 * 因此端口的声明不能藏在实现层里。端口本身不含默认实现，也不提供安装/替换入口；
 * 实例由模块装配根构造后注入 {@code YoBluetoothPermissionChecker}。</p>
 */
public interface BluetoothPermissionPort {

    /**
     * 检查指定蓝牙权限当前是否已授予。
     */
    boolean has(@NonNull Context context, @NonNull YoBluetoothPermission permission);
}
