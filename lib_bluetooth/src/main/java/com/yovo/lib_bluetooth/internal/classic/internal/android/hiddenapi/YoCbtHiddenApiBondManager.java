package com.yovo.lib_bluetooth.internal.classic.internal.android.hiddenapi;

import android.bluetooth.BluetoothDevice;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 隐藏 API 配对管理，通过反射执行 createBond/removeBond 等操作。
 */

public final class YoCbtHiddenApiBondManager {
    private YoCbtHiddenApiBondManager() {
    }

    public static boolean cancelBondProcess(@NonNull BluetoothDevice device,
                                            @NonNull YoCbtHiddenApiPolicy policy) throws IOException {
        return invokeBoolean(device, "cancelBondProcess", policy);
    }

    public static boolean removeBond(@NonNull BluetoothDevice device,
                                     @NonNull YoCbtHiddenApiPolicy policy) throws IOException {
        return invokeBoolean(device, "removeBond", policy);
    }

    private static boolean invokeBoolean(BluetoothDevice device,
                                        String methodName,
                                        YoCbtHiddenApiPolicy policy) throws IOException {
        if (policy != YoCbtHiddenApiPolicy.EXPLICITLY_ENABLED) {
            throw new IOException(methodName + " hidden API is disabled by policy");
        }
        try {
            Method method = device.getClass().getMethod(methodName);
            Object result = method.invoke(device);
            return result instanceof Boolean && (Boolean) result;
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            throw new IOException(methodName + " failed", cause);
        } catch (ReflectiveOperationException e) {
            throw new IOException(methodName + " unavailable", e);
        }
    }
}
