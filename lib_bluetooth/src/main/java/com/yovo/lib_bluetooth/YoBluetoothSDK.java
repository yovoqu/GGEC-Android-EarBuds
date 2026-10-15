package com.yovo.lib_bluetooth;

import android.content.Context;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.api.YoBleClient;
import com.yovo.lib_bluetooth.api.YoCbtClient;
import com.yovo.lib_bluetooth.internal.BluetoothSdkCore;
import com.yovo.lib_bluetooth.model.ble.BleConfig;
import com.yovo.lib_bluetooth.model.classic.CbtConfig;

/**
 * lib_bluetooth 唯一对外静态门面。创建客户端时自动安装日志/权限 Provider。
 */
public final class YoBluetoothSDK {

    private YoBluetoothSDK() {
    }

    @NonNull
    public static YoCbtClient classic(@NonNull Context context) {
        return BluetoothSdkCore.classic(context, CbtConfig.builder().build());
    }

    @NonNull
    public static YoCbtClient classic(@NonNull Context context, @NonNull CbtConfig config) {
        return BluetoothSdkCore.classic(context, config);
    }

    @NonNull
    public static YoBleClient ble(@NonNull Context context) {
        return BluetoothSdkCore.ble(context, BleConfig.builder().build());
    }

    @NonNull
    public static YoBleClient ble(@NonNull Context context, @NonNull BleConfig config) {
        return BluetoothSdkCore.ble(context, config);
    }
}
