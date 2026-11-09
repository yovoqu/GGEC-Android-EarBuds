package com.yovo.lib_bluetooth.internal.ble.internal.android.hiddenapi;

import android.bluetooth.BluetoothDevice;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.common.diagnostics.YoBluetoothLogger;

import java.lang.reflect.Method;

/**
 * BLE 配对反射封装（受 {@link YoBleHiddenApiPolicy} 控制 {@code removeBond}，并兼容 {@code cancelBondProcess}）。
 */
public final class YoBleBondReflection {

    private static final String TAG = "YoBleBondReflection";

    private YoBleBondReflection() {
    }

    public static boolean cancelBondProcess(@NonNull BluetoothDevice device) {
        try {
            Method method = device.getClass().getMethod("cancelBondProcess");
            Object res = method.invoke(device);
            return res instanceof Boolean && (Boolean) res;
        } catch (Throwable t) {
            YoBluetoothLogger.w(TAG, "cancelBondProcess unavailable", t);
            return false;
        }
    }

    public static boolean removeBond(@NonNull BluetoothDevice device,
                                     @NonNull YoBleHiddenApiPolicy policy) {
        if (policy != YoBleHiddenApiPolicy.EXPLICITLY_ENABLED) {
            YoBluetoothLogger.w(TAG, "removeBond blocked by YoBleHiddenApiPolicy.DISABLED");
            return false;
        }
        try {
            Method method = device.getClass().getMethod("removeBond");
            Object res = method.invoke(device);
            return res instanceof Boolean && (Boolean) res;
        } catch (Throwable t) {
            YoBluetoothLogger.w(TAG, "removeBond reflection failed", t);
            return false;
        }
    }
}
