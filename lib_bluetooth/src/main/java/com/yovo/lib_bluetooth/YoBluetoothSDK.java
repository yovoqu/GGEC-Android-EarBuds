package com.yovo.lib_bluetooth;

import android.content.Context;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.api.YoBleClient;
import com.yovo.lib_bluetooth.api.YoCbtClient;
import com.yovo.lib_bluetooth.internal.BluetoothSdkCore;
import com.yovo.lib_bluetooth.model.ble.BleConfig;
import com.yovo.lib_bluetooth.model.classic.CbtConfig;
import com.yovo.lib_bluetooth.provider.lib_common.log.YoBtLogger;
import com.yovo.lib_bluetooth.provider.lib_common.permission.BluetoothPermissionProvider;
import com.yovo.lib_bluetooth.spi.log.LogSink;
import com.yovo.lib_bluetooth.spi.permission.BluetoothPermissionPort;

/**
 * lib_bluetooth 唯一对外静态门面，同时是模块的<b>装配根</b>。
 *
 * <p>SPI 实现的构造点只有这里：{@link BluetoothPermissionProvider}（权限）与
 * {@link YoBtLogger#SINK}（日志）在此构造后注入 {@link BluetoothSdkCore}，
 * 再沿客户端实现向下传递。实现层与 provider 因此互不依赖，只共同依赖 {@code spi/}。
 * 本类不持有任何静态可写状态。</p>
 */
public final class YoBluetoothSDK {

    private YoBluetoothSDK() {
    }

    @NonNull
    public static YoCbtClient classic(@NonNull Context context) {
        return classic(context, CbtConfig.builder().build());
    }

    @NonNull
    public static YoCbtClient classic(@NonNull Context context, @NonNull CbtConfig config) {
        return BluetoothSdkCore.classic(context, config, permissionPort(), logSink());
    }

    @NonNull
    public static YoBleClient ble(@NonNull Context context) {
        return ble(context, BleConfig.builder().build());
    }

    @NonNull
    public static YoBleClient ble(@NonNull Context context, @NonNull BleConfig config) {
        return BluetoothSdkCore.ble(context, config, permissionPort(), logSink());
    }

    @NonNull
    private static BluetoothPermissionPort permissionPort() {
        return new BluetoothPermissionProvider();
    }

    @NonNull
    private static LogSink logSink() {
        return YoBtLogger.SINK;
    }
}
