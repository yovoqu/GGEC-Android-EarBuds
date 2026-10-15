package com.yovo.lib_bluetooth.provider.lib_common;

import com.yovo.lib_bluetooth.internal.common.diagnostics.YoBluetoothLogger;
import com.yovo.lib_bluetooth.internal.common.permission.YoBluetoothPermissionChecker;

/**
 * lib_common Provider 安装入口：在 SDK 客户端创建前调用，保证日志/权限走统一端口。
 */
public final class BluetoothProviderInstaller {

    private static volatile boolean installed;

    private BluetoothProviderInstaller() {
    }

    public static void installAll() {
        if (installed) {
            return;
        }
        synchronized (BluetoothProviderInstaller.class) {
            if (installed) {
                return;
            }
            YoBluetoothLogger.setPort(LogProvider.INSTANCE);
            YoBluetoothPermissionChecker.setPort(PermissionProvider.INSTANCE);
            installed = true;
        }
    }
}
