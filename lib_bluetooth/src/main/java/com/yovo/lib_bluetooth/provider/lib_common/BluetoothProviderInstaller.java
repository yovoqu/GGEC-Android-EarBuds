package com.yovo.lib_bluetooth.provider.lib_common;

import com.yovo.lib_bluetooth.internal.common.permission.YoBluetoothPermissionChecker;

/**
 * lib_common Provider 安装入口：在 SDK 客户端创建前调用。
 *
 * <p>日志已由 {@code YoBtLogger} 直接委托 {@code YoLog}，无需安装；此处仅安装权限端口。</p>
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
            YoBluetoothPermissionChecker.setPort(PermissionProvider.INSTANCE);
            installed = true;
        }
    }
}
