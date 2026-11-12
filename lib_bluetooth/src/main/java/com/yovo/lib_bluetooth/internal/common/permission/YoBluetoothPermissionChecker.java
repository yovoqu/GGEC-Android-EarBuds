package com.yovo.lib_bluetooth.internal.common.permission;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.EnumSet;
import java.util.Objects;

/**
 * 检查应用是否已授予指定蓝牙相关权限，统一委托构造注入的 {@link BluetoothPermissionPort}。
 *
 * <p>端口实例由装配根 {@code BluetoothSdkCore} 创建并沿客户端实现向下传；
 * 本类不持有任何静态状态，也不提供安装/替换入口。</p>
 */
public final class YoBluetoothPermissionChecker {

    @NonNull
    private final BluetoothPermissionPort port;

    public YoBluetoothPermissionChecker(@NonNull BluetoothPermissionPort port) {
        this.port = Objects.requireNonNull(port);
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
