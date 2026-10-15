package com.yovo.lib_bluetooth.internal.common.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;

import androidx.annotation.Nullable;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 通过 Context 获取系统 {@link BluetoothAdapter} 的提供者。
 */

public final class YoBluetoothAdapterProvider {
    private final Context context;

    public YoBluetoothAdapterProvider(Context context) {
        this.context = context == null ? null : context.getApplicationContext();
    }

    @Nullable
    public BluetoothAdapter adapter() {
        if (context == null) return null;
        BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        return manager == null ? null : manager.getAdapter();
    }
}
