package com.yovo.lib_bluetooth.internal;

import android.content.Context;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.api.YoBleClient;
import com.yovo.lib_bluetooth.api.YoCbtClient;
import com.yovo.lib_bluetooth.internal.ble.BleClientImpl;
import com.yovo.lib_bluetooth.internal.classic.ClassicClientImpl;
import com.yovo.lib_bluetooth.internal.common.permission.YoBluetoothPermissionChecker;
import com.yovo.lib_bluetooth.model.ble.BleConfig;
import com.yovo.lib_bluetooth.model.classic.CbtConfig;
import com.yovo.lib_bluetooth.provider.lib_common.log.YoBtLogger;
import com.yovo.lib_bluetooth.provider.lib_common.permission.BluetoothPermissionProvider;

/**
 * SDK 装配根：创建 {@code provider/lib_common} 的权限实现与日志缝，再沿客户端实现向下构造注入。
 *
 * <p>本模块的 Provider 只在此处构造，不提供安装入口、静态端口或任何全局可写状态。</p>
 */
public final class BluetoothSdkCore {

    private BluetoothSdkCore() {
    }

    @NonNull
    public static YoCbtClient classic(@NonNull Context context, @NonNull CbtConfig config) {
        YoBtLogger.ensureInitialized(context);
        return new ClassicClientImpl(context, config, permissionChecker());
    }

    @NonNull
    public static YoBleClient ble(@NonNull Context context, @NonNull BleConfig config) {
        YoBtLogger.ensureInitialized(context);
        return new BleClientImpl(context, config, permissionChecker());
    }

    @NonNull
    private static YoBluetoothPermissionChecker permissionChecker() {
        return new YoBluetoothPermissionChecker(new BluetoothPermissionProvider());
    }
}
