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
import com.yovo.lib_bluetooth.spi.log.LogSink;
import com.yovo.lib_bluetooth.spi.permission.BluetoothPermissionPort;

/**
 * SDK 装配根：把根门面注入的 SPI 实现（权限端口、日志端口）包成本模块的权限检查器，
 * 再沿客户端实现向下构造注入。
 *
 * <p><b>provider 构造点在根门面。</b>本类不 new 任何 provider 实现：权限端口与 {@link LogSink}
 * 由 {@code YoBluetoothSDK} 构造后传入，因此 {@code provider/**} 只被根门面引用，
 * 依赖方向固定为 provider → spi、internal → spi，provider 不再反向依赖 internal。</p>
 *
 * <p>日志的唯一接入动作是 {@link YoBtLogger#requireInitialized()}：它只断言宿主已完成日志初始化，
 * 宿主没初始化就在这里直接报错（不代宿主初始化、不缓存状态）。</p>
 */
public final class BluetoothSdkCore {

    private BluetoothSdkCore() {
    }

    @NonNull
    public static YoCbtClient classic(@NonNull Context context,
                                      @NonNull CbtConfig config,
                                      @NonNull BluetoothPermissionPort permissionPort,
                                      @NonNull LogSink logSink) {
        YoBtLogger.requireInitialized();
        return new ClassicClientImpl(context, config,
                new YoBluetoothPermissionChecker(permissionPort), logSink);
    }

    @NonNull
    public static YoBleClient ble(@NonNull Context context,
                                  @NonNull BleConfig config,
                                  @NonNull BluetoothPermissionPort permissionPort,
                                  @NonNull LogSink logSink) {
        YoBtLogger.requireInitialized();
        return new BleClientImpl(context, config,
                new YoBluetoothPermissionChecker(permissionPort), logSink);
    }
}
