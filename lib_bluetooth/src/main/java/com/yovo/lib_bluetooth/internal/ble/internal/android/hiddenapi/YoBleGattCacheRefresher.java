package com.yovo.lib_bluetooth.internal.ble.internal.android.hiddenapi;

import android.bluetooth.BluetoothGatt;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.common.diagnostics.YoBtLogger;

import java.lang.reflect.Method;

/**
 * GATT 缓存刷新封装（封装隐藏 API {@code BluetoothGatt#refresh()}，受策略门控与异常保护）。
 */
public final class YoBleGattCacheRefresher {

    private static final String TAG = "YoBleCacheRefresher";

    private YoBleGattCacheRefresher() {
    }

    public static boolean refresh(@NonNull BluetoothGatt gatt,
                                  @NonNull YoBleHiddenApiPolicy policy) {
        if (policy != YoBleHiddenApiPolicy.EXPLICITLY_ENABLED) {
            YoBtLogger.w(TAG, "BluetoothGatt.refresh() blocked by YoBleHiddenApiPolicy.DISABLED");
            return false;
        }
        try {
            Method method = gatt.getClass().getMethod("refresh");
            Object result = method.invoke(gatt);
            boolean ok = result instanceof Boolean && (Boolean) result;
            YoBtLogger.d(TAG, "BluetoothGatt.refresh() -> " + ok);
            return ok;
        } catch (Throwable t) {
            YoBtLogger.w(TAG, "BluetoothGatt.refresh() reflection failed", t);
            return false;
        }
    }
}
