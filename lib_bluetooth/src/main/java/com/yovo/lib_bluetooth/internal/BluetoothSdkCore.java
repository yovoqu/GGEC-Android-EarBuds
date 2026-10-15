package com.yovo.lib_bluetooth.internal;

import android.content.Context;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.api.YoBleClient;
import com.yovo.lib_bluetooth.api.YoCbtClient;
import com.yovo.lib_bluetooth.internal.ble.BleClientImpl;
import com.yovo.lib_bluetooth.internal.classic.ClassicClientImpl;
import com.yovo.lib_bluetooth.model.ble.BleConfig;
import com.yovo.lib_bluetooth.model.classic.CbtConfig;
import com.yovo.lib_bluetooth.provider.lib_common.BluetoothProviderInstaller;

/**
 * SDK 创建核心：创建客户端前必须安装 lib_common Provider。
 */
public final class BluetoothSdkCore {

    private BluetoothSdkCore() {
    }

    @NonNull
    public static YoCbtClient classic(@NonNull Context context, @NonNull CbtConfig config) {
        BluetoothProviderInstaller.installAll();
        return new ClassicClientImpl(context, config);
    }

    @NonNull
    public static YoBleClient ble(@NonNull Context context, @NonNull BleConfig config) {
        BluetoothProviderInstaller.installAll();
        return new BleClientImpl(context, config);
    }
}
