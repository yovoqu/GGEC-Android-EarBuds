package com.ggec.earbuds.provider.lib_common;

import android.content.Context;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.device.port.ScanPermissionPort;

/**
 * 用 {@code lib_common} 权限域回答「蓝牙扫描权限是否已授予」。
 * 只做查询；弹窗与结果回收仍由 Activity 通过 {@link PermissionProvider} 完成。
 */
public final class BluetoothScanPermission implements ScanPermissionPort {

    private final Context appContext;

    public BluetoothScanPermission(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
    }

    @Override
    public boolean isGranted() {
        return PermissionProvider.isBluetoothGranted(appContext);
    }
}
